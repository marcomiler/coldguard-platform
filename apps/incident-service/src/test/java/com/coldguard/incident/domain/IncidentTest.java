package com.coldguard.incident.domain;

import static com.coldguard.incident.domain.DomainFixtures.SLA;
import static com.coldguard.incident.domain.DomainFixtures.T0;
import static com.coldguard.incident.domain.DomainFixtures.clockAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IncidentTest {

  private static final Incident MEDIUM = DomainFixtures.openMedium();

  @Test
  void open_derivesPriorityAndSlaFromCreationTime() {
    Transition opened =
        Incident.open(
            "id",
            "a",
            "s",
            "t",
            Criticality.CRITICAL,
            Magnitude.CRITICAL,
            true,
            null,
            SLA,
            clockAt(T0));

    Incident incident = opened.incident();
    assertThat(incident.status()).isEqualTo(IncidentStatus.CREATED);
    assertThat(incident.priority()).isEqualTo(Priority.P1);
    assertThat(incident.ackDueAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    assertThat(incident.resolveDueAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
    assertThat(incident.occurrenceCount()).isEqualTo(1);
    assertThat(opened.event()).isInstanceOf(IncidentEvent.Created.class);
  }

  @Test
  void open_p4HasNoResolutionTarget() {
    Incident low = DomainFixtures.open(Criticality.LOW, Magnitude.LOW, false);

    assertThat(low.priority()).isEqualTo(Priority.P4);
    assertThat(low.resolveDueAt()).isNull();
    assertThat(low.ackDueAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
  }

  @Test
  void acknowledge_fromCreated_movesToAcknowledgedAndRecordsWho() {
    Transition t = MEDIUM.acknowledge("sup-1", clockAt(T0.plusSeconds(60)));

    assertThat(t.incident().status()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    assertThat(t.incident().acknowledgedAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(t.incident().acknowledgedBy()).isEqualTo("sup-1");
    assertThat(t.event()).isInstanceOf(IncidentEvent.Acknowledged.class);
  }

  @Test
  void acknowledge_whenEscalated_keepsEscalated() {
    Incident escalated = MEDIUM.escalate("no response", clockAt(T0.plusSeconds(10))).incident();

    Incident acknowledged = escalated.acknowledge("sup-1", clockAt(T0.plusSeconds(20))).incident();

    assertThat(acknowledged.status()).isEqualTo(IncidentStatus.ESCALATED);
    assertThat(acknowledged.acknowledgedAt()).isNotNull();
  }

  @Test
  void acknowledge_twice_isRejected() {
    Incident acknowledged = MEDIUM.acknowledge("sup-1", clockAt(T0)).incident();

    assertThatThrownBy(() -> acknowledged.acknowledge("sup-2", clockAt(T0)))
        .isInstanceOf(IncidentAlreadyAcknowledgedException.class);
  }

  @Test
  void escalate_isRepeatableAndCounts() {
    Incident once = MEDIUM.escalate("first", clockAt(T0.plusSeconds(1))).incident();

    Incident twice = once.escalate("second", clockAt(T0.plusSeconds(2))).incident();

    assertThat(twice.escalationCount()).isEqualTo(2);
    assertThat(twice.lastEscalatedAt()).isEqualTo(T0.plusSeconds(2));
    assertThat(twice.status()).isEqualTo(IncidentStatus.ESCALATED);
  }

  @Test
  void escalate_requiresReason() {
    assertThatThrownBy(() -> MEDIUM.escalate(" ", clockAt(T0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    assertThatThrownBy(() -> MEDIUM.escalate(null, clockAt(T0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void close_recordsEvidenceFromAnyOpenStateEvenWithoutAcknowledgement() {
    Transition t =
        MEDIUM.close("tech-1", new CloseEvidence("overheating", "replaced sensor"), clockAt(T0));

    Incident closed = t.incident();
    assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(closed.closedAt()).isEqualTo(T0);
    assertThat(closed.closedBy()).isEqualTo("tech-1");
    assertThat(closed.cause()).isEqualTo("overheating");
    assertThat(closed.resolutionComment()).isEqualTo("replaced sensor");
    assertThat(closed.acknowledgedAt()).isNull();
  }

  @Test
  void close_evidenceIsRequiredAndBounded() {
    assertThatThrownBy(() -> new CloseEvidence(" ", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cause");
    assertThatThrownBy(() -> new CloseEvidence("x", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolutionComment");
    assertThatThrownBy(() -> new CloseEvidence("x".repeat(501), "y"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CloseEvidence("x", "y".repeat(2001)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void closedIncident_rejectsEveryFurtherTransition() {
    Incident closed = MEDIUM.close("tech-1", new CloseEvidence("c", "r"), clockAt(T0)).incident();

    assertThatThrownBy(() -> closed.acknowledge("s", clockAt(T0)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
    assertThatThrownBy(() -> closed.escalate("r", clockAt(T0)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
    assertThatThrownBy(() -> closed.close("t", new CloseEvidence("c", "r"), clockAt(T0)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
    assertThatThrownBy(() -> closed.registerOccurrence(Magnitude.LOW, false, SLA, clockAt(T0)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
  }

  @Test
  void registerOccurrence_countsAndKeepsPriorityWhenUrgencyDoesNotRise() {
    Transition t = MEDIUM.registerOccurrence(Magnitude.LOW, false, SLA, clockAt(T0.plusSeconds(5)));

    Incident updated = t.incident();
    assertThat(updated.occurrenceCount()).isEqualTo(2);
    assertThat(updated.lastOccurrenceAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(updated.urgency()).isEqualTo(MEDIUM.urgency());
    assertThat(updated.priority()).isEqualTo(MEDIUM.priority());
    assertThat(updated.ackDueAt()).isEqualTo(MEDIUM.ackDueAt());
    assertThat(((IncidentEvent.OccurrenceRegistered) t.event()).priorityRecalculated()).isFalse();
  }

  @Test
  void registerOccurrence_persistenceRaisesUrgencyAndRecomputesSlaFromCreation() {
    // MEDIUM criticality + MEDIUM magnitude: urgency MEDIUM, P3. Persistent -> HIGH, still P3
    // (matrix), so use a HIGH-impact incident where HIGH->IMMEDIATE moves P2 to P1.
    Incident p2 = DomainFixtures.open(Criticality.HIGH, Magnitude.HIGH, false);
    assertThat(p2.priority()).isEqualTo(Priority.P2);

    Transition t = p2.registerOccurrence(Magnitude.HIGH, true, SLA, clockAt(T0.plusSeconds(600)));

    Incident updated = t.incident();
    assertThat(updated.urgency()).isEqualTo(Urgency.IMMEDIATE);
    assertThat(updated.priority()).isEqualTo(Priority.P1);
    assertThat(updated.persistent()).isTrue();
    assertThat(updated.ackDueAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    assertThat(updated.resolveDueAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
    IncidentEvent.OccurrenceRegistered event = (IncidentEvent.OccurrenceRegistered) t.event();
    assertThat(event.priorityRecalculated()).isTrue();
    assertThat(event.previousPriority()).isEqualTo(Priority.P2);
  }

  @Test
  void registerOccurrence_neverLowersUrgency() {
    Incident critical = DomainFixtures.open(Criticality.CRITICAL, Magnitude.CRITICAL, true);

    Incident updated =
        critical
            .registerOccurrence(Magnitude.LOW, false, SLA, clockAt(T0.plusSeconds(1)))
            .incident();

    assertThat(updated.urgency()).isEqualTo(Urgency.IMMEDIATE);
    assertThat(updated.priority()).isEqualTo(Priority.P1);
    assertThat(updated.persistent()).isTrue();
  }
}
