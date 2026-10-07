package com.coldguard.incident.application;

import static com.coldguard.incident.application.ApplicationFixtures.SUPERVISOR;
import static com.coldguard.incident.application.ApplicationFixtures.TECHNICIAN;
import static com.coldguard.incident.domain.DomainFixtures.SLA;
import static com.coldguard.incident.domain.DomainFixtures.T0;
import static com.coldguard.incident.domain.DomainFixtures.clockAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.ApplicationFixtures.CapturedAudit;
import com.coldguard.incident.application.ApplicationFixtures.CapturedEvents;
import com.coldguard.incident.application.ApplicationFixtures.FixedRecipients;
import com.coldguard.incident.application.ApplicationFixtures.InMemoryIncidents;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentAlreadyAcknowledgedException;
import com.coldguard.incident.domain.IncidentAlreadyClosedException;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Use cases against in-memory ports: authorization, state changes and the events they emit. */
class IncidentLifecycleServicesTest {

  private static final Clock CLOCK = clockAt(T0);

  private final InMemoryIncidents incidents = new InMemoryIncidents();
  private final CapturedEvents captured = new CapturedEvents();
  private final FixedRecipients recipients = new FixedRecipients();
  private final CapturedAudit audit = new CapturedAudit();
  private final IncidentEventPublisher events =
      new IncidentEventPublisher(captured, recipients, new IncidentAuditTrail(audit), event -> {});
  private final IncidentOpener opener = new IncidentOpener(incidents, SLA, events, CLOCK);

  private final CreateIncidentService create = new CreateIncidentService(incidents, opener);
  private final HandleThresholdBreachService breach =
      new HandleThresholdBreachService(incidents, opener, events, SLA, CLOCK);
  private final AcknowledgeIncidentService acknowledge =
      new AcknowledgeIncidentService(incidents, events, CLOCK);
  private final EscalateIncidentService escalate =
      new EscalateIncidentService(incidents, events, CLOCK);
  private final CloseIncidentService close = new CloseIncidentService(incidents, events, CLOCK);

  private static OpenIncidentCommand anomaly(Magnitude magnitude, boolean persistent) {
    return new OpenIncidentCommand(
        "asset-1",
        Criticality.HIGH,
        "sensor-1",
        "TEMPERATURE_ABOVE_MAX",
        magnitude,
        persistent,
        "r1");
  }

  private Incident openOne() {
    return create.create(new CreateIncidentCommand(anomaly(Magnitude.HIGH, false), null));
  }

  @Test
  void create_emitsIncidentCreatedAndNotificationToSupervisors() {
    Incident incident = openOne();

    assertThat(incident.priority()).isEqualTo(Priority.P2);
    assertThat(captured.types()).containsExactly("IncidentCreated", "NotificationRequested");
    OutboundEvent notification = captured.published.get(1);
    assertThat(notification.routingKey()).isEqualTo("incident.notification-requested");
    assertThat(notification.payload().toString())
        .contains("INCIDENT_CREATED", "sup@coldguard.test");
    assertThat(captured.published.get(0).actor()).isEqualTo(EventActor.system("incident-api"));
  }

  @Test
  void create_noRecipients_stillCreatesButRequestsNoNotification() {
    recipients.byRole.clear();

    openOne();

    assertThat(captured.types()).containsExactly("IncidentCreated");
  }

  @Test
  void create_whenOpenExists_reportsExistingId() {
    Incident first = openOne();

    assertThatThrownBy(() -> openOne())
        .isInstanceOfSatisfying(
            IncidentAlreadyOpenException.class,
            ex -> assertThat(ex.getExistingIncidentId()).isEqualTo(first.id()));
  }

  @Test
  void create_lostRace_reportsTheWinner() {
    Incident winner = openOne();
    // Hides the winner from the pre-check, then fails the insert like the unique index would.
    InMemoryIncidents racing =
        new InMemoryIncidents() {
          private boolean precheck = true;

          @Override
          public Optional<Incident> findOpen(String a, String s, String t) {
            if (precheck) {
              precheck = false;
              return Optional.empty();
            }
            return incidents.findOpen(a, s, t);
          }

          @Override
          public void save(Incident incident) {
            throw new DuplicateIncidentException("race", null);
          }
        };
    CreateIncidentService service =
        new CreateIncidentService(racing, new IncidentOpener(racing, SLA, events, CLOCK));

    assertThatThrownBy(
            () -> service.create(new CreateIncidentCommand(anomaly(Magnitude.HIGH, false), null)))
        .isInstanceOfSatisfying(
            IncidentAlreadyOpenException.class,
            ex -> assertThat(ex.getExistingIncidentId()).isEqualTo(winner.id()));
  }

  @Test
  void thresholdBreach_noOpenIncident_opensOneAndTracesSourceReading() {
    breach.handle(anomaly(Magnitude.HIGH, false));

    assertThat(incidents.byId).hasSize(1);
    assertThat(incidents.byId.values().iterator().next().sourceReadingId()).isEqualTo("r1");
    assertThat(captured.types()).containsExactly("IncidentCreated", "NotificationRequested");
    assertThat(captured.published.get(0).actor()).isEqualTo(EventActor.system("incident-service"));
  }

  @Test
  void thresholdBreach_openIncidentExists_registersOccurrenceWithoutNewEvents() {
    breach.handle(anomaly(Magnitude.HIGH, false));
    captured.published.clear();

    breach.handle(anomaly(Magnitude.HIGH, false));

    assertThat(incidents.byId).hasSize(1);
    Incident incident = incidents.byId.values().iterator().next();
    assertThat(incident.occurrenceCount()).isEqualTo(2);
    assertThat(captured.published).isEmpty();
  }

  @Test
  void thresholdBreach_persistenceRaisesPriorityOfOpenIncident() {
    breach.handle(anomaly(Magnitude.HIGH, false));

    breach.handle(anomaly(Magnitude.HIGH, true));

    assertThat(incidents.byId.values().iterator().next().priority()).isEqualTo(Priority.P1);
  }

  @Test
  void thresholdBreach_afterClose_opensANewIncident() {
    Incident first = openOne();
    close.close(new CloseIncidentCommand(first.id(), "cause", "comment", TECHNICIAN));

    breach.handle(anomaly(Magnitude.HIGH, false));

    assertThat(incidents.byId).hasSize(2);
    assertThat(incidents.findOpen("asset-1", "sensor-1", "TEMPERATURE_ABOVE_MAX")).isPresent();
  }

  @Test
  void thresholdBreach_raceOnInsert_propagatesSoTheDeliveryIsRetried() {
    incidents.failNextSave = new DuplicateIncidentException("race", null);

    assertThatThrownBy(() -> breach.handle(anomaly(Magnitude.HIGH, false)))
        .isInstanceOf(DuplicateIncidentException.class);
  }

  @Test
  void acknowledge_supervisor_acknowledgesAndEmitsEventWithActor() {
    Incident incident = openOne();
    captured.published.clear();

    Incident result =
        acknowledge.acknowledge(new AcknowledgeIncidentCommand(incident.id(), SUPERVISOR));

    assertThat(result.status()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    assertThat(captured.types()).containsExactly("IncidentAcknowledged");
    assertThat(captured.published.get(0).actor()).isEqualTo(EventActor.user("sup-1"));
  }

  @Test
  void acknowledge_twice_isRejected() {
    Incident incident = openOne();
    acknowledge.acknowledge(new AcknowledgeIncidentCommand(incident.id(), SUPERVISOR));

    assertThatThrownBy(
            () ->
                acknowledge.acknowledge(new AcknowledgeIncidentCommand(incident.id(), SUPERVISOR)))
        .isInstanceOf(IncidentAlreadyAcknowledgedException.class);
  }

  @Test
  void acknowledge_otherRoleOrNoActor_isForbiddenBeforeAnyLookup() {
    for (Actor actor : new Actor[] {TECHNICIAN, null, new Actor("op", Set.of(Role.OPERATOR))}) {
      assertThatThrownBy(() -> acknowledge.acknowledge(new AcknowledgeIncidentCommand("x", actor)))
          .isInstanceOf(ActorNotAuthorizedException.class);
    }
  }

  @Test
  void acknowledge_unknownIncident_isNotFound() {
    assertThatThrownBy(
            () -> acknowledge.acknowledge(new AcknowledgeIncidentCommand("missing", SUPERVISOR)))
        .isInstanceOf(IncidentNotFoundException.class);
  }

  @Test
  void escalate_notifiesTechniciansAndIsRepeatable() {
    Incident incident = openOne();
    captured.published.clear();

    escalate.escalate(new EscalateIncidentCommand(incident.id(), "no response", SUPERVISOR));
    Incident again =
        escalate.escalate(new EscalateIncidentCommand(incident.id(), "still down", SUPERVISOR));

    assertThat(again.escalationCount()).isEqualTo(2);
    assertThat(captured.types())
        .containsExactly(
            "IncidentEscalated",
            "NotificationRequested",
            "IncidentEscalated",
            "NotificationRequested");
    assertThat(captured.published.get(1).payload().toString())
        .contains("INCIDENT_ESCALATED", "tech@coldguard.test");
  }

  @Test
  void escalate_withoutReason_isInvalidAndEmitsNothing() {
    Incident incident = openOne();
    captured.published.clear();

    assertThatThrownBy(
            () -> escalate.escalate(new EscalateIncidentCommand(incident.id(), " ", SUPERVISOR)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(captured.published).isEmpty();
  }

  @Test
  void escalate_technician_isForbidden() {
    assertThatThrownBy(
            () -> escalate.escalate(new EscalateIncidentCommand("x", "reason", TECHNICIAN)))
        .isInstanceOf(ActorNotAuthorizedException.class);
  }

  @Test
  void close_technician_persistsEvidenceAndEmitsEvent() {
    Incident incident = openOne();
    captured.published.clear();

    Incident closed =
        close.close(new CloseIncidentCommand(incident.id(), "overheating", "replaced", TECHNICIAN));

    assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(incidents.byId.get(incident.id()).closedBy()).isEqualTo("tech-1");
    assertThat(captured.types()).containsExactly("IncidentClosed");
  }

  @Test
  void close_supervisor_isForbidden() {
    assertThatThrownBy(() -> close.close(new CloseIncidentCommand("x", "c", "r", SUPERVISOR)))
        .isInstanceOf(ActorNotAuthorizedException.class);
  }

  @Test
  void close_missingEvidence_isInvalidBeforeAnyLookup() {
    assertThatThrownBy(() -> close.close(new CloseIncidentCommand("missing", " ", "r", TECHNICIAN)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void close_alreadyClosed_isRejected() {
    Incident incident = openOne();
    close.close(new CloseIncidentCommand(incident.id(), "c", "r", TECHNICIAN));

    assertThatThrownBy(
            () -> close.close(new CloseIncidentCommand(incident.id(), "c", "r", TECHNICIAN)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
  }

  @Test
  void list_rejectsPagesAboveTheConfiguredMaximumAndAppliesTheDefault() {
    IncidentQueryService query =
        new IncidentQueryService(incidents, new IncidentQueryLimits(20, 100));
    IncidentSearch any = new IncidentSearch(null, null, null, null, null, null);

    assertThatThrownBy(() -> query.list(any, new PageQuery(0, 101)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(query.list(any, new PageQuery(0, 0)).size()).isEqualTo(20);
  }

  @Test
  void everyTransitionIsAuditedWithActorAndReason() {
    Incident incident = openOne();
    acknowledge.acknowledge(new AcknowledgeIncidentCommand(incident.id(), SUPERVISOR));
    escalate.escalate(new EscalateIncidentCommand(incident.id(), "no response", SUPERVISOR));
    close.close(new CloseIncidentCommand(incident.id(), "overheating", "replaced", TECHNICIAN));

    assertThat(audit.actions()).containsExactly("CREATED", "ACKNOWLEDGED", "ESCALATED", "CLOSED");
    assertThat(audit.entries.get(0).actorId()).isEqualTo("incident-api");
    assertThat(audit.entries.get(0).actorType().name()).isEqualTo("SYSTEM");
    assertThat(audit.entries.get(2).reason()).isEqualTo("no response");
    assertThat(audit.entries.get(2).actorId()).isEqualTo("sup-1");
    assertThat(audit.entries.get(3).actorId()).isEqualTo("tech-1");
    assertThat(audit.entries.get(3).reason()).isEqualTo("overheating");
  }

  @Test
  void priorityRecalculationIsAuditedWithPreviousAndNewValue() {
    breach.handle(anomaly(Magnitude.HIGH, false));
    audit.entries.clear();

    breach.handle(anomaly(Magnitude.HIGH, true));

    assertThat(audit.actions()).containsExactly("OCCURRENCE_REGISTERED", "PRIORITY_RECALCULATED");
    assertThat(audit.entries.get(1).before()).contains("P2");
    assertThat(audit.entries.get(1).after()).contains("P1");
  }

  @Test
  void occurrenceWithoutPriorityChange_isAuditedOnlyAsOccurrence() {
    breach.handle(anomaly(Magnitude.HIGH, false));
    audit.entries.clear();

    breach.handle(anomaly(Magnitude.HIGH, false));

    assertThat(audit.actions()).containsExactly("OCCURRENCE_REGISTERED");
  }
}
