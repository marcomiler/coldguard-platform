package com.coldguard.incident.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentAlreadyClosedException;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure unit test: IncidentRepository is mocked, no database involved. */
class CloseIncidentServiceTest {

  private static final String AUTHORIZED_ROLE = "ROLE_MAINTENANCE_TECHNICIAN";

  private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
  private final CloseIncidentService service = new CloseIncidentService(incidentRepository);

  @Test
  void close_authorizedActor_persistsClosedIncident() {
    Incident incident = newIncident("incident-1", IncidentStatus.CREATED);
    given(incidentRepository.findById("incident-1")).willReturn(Optional.of(incident));

    Incident closed =
        service.close(
            new CloseIncidentCommand(
                "incident-1", "overheating", "replaced sensor", AUTHORIZED_ROLE));

    assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    verify(incidentRepository).update(closed);
  }

  @Test
  void close_unauthorizedActor_throwsIncidentCloseForbiddenException() {
    assertThatThrownBy(
            () ->
                service.close(
                    new CloseIncidentCommand(
                        "incident-1", "overheating", "replaced sensor", "ROLE_SUPERVISOR")))
        .isInstanceOf(IncidentCloseForbiddenException.class);
  }

  @Test
  void close_missingCause_throwsIllegalArgumentException() {
    assertThatThrownBy(
            () ->
                service.close(
                    new CloseIncidentCommand(
                        "incident-1", " ", "replaced sensor", AUTHORIZED_ROLE)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void close_missingResolutionComment_throwsIllegalArgumentException() {
    assertThatThrownBy(
            () ->
                service.close(
                    new CloseIncidentCommand("incident-1", "overheating", " ", AUTHORIZED_ROLE)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void close_unknownIncident_throwsIncidentNotFoundException() {
    given(incidentRepository.findById("missing")).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.close(
                    new CloseIncidentCommand(
                        "missing", "overheating", "replaced sensor", AUTHORIZED_ROLE)))
        .isInstanceOf(IncidentNotFoundException.class);
  }

  @Test
  void close_alreadyClosedIncident_throwsIncidentAlreadyClosedException() {
    Incident incident = newIncident("incident-2", IncidentStatus.CLOSED);
    given(incidentRepository.findById("incident-2")).willReturn(Optional.of(incident));

    assertThatThrownBy(
            () ->
                service.close(
                    new CloseIncidentCommand(
                        "incident-2", "overheating", "replaced sensor", AUTHORIZED_ROLE)))
        .isInstanceOf(IncidentAlreadyClosedException.class);
  }

  private static Incident newIncident(String id, IncidentStatus status) {
    return new Incident(
        id,
        "asset-1",
        "sensor-1",
        "high-temperature",
        Impact.HIGH,
        Urgency.HIGH,
        Priority.P2,
        status,
        Instant.now());
  }
}
