package com.coldguard.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IncidentTest {

  @Test
  void close_createdIncident_returnsClosedCopy() {
    Incident incident = newIncident(IncidentStatus.CREATED);

    Incident closed = incident.close();

    assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(closed.id()).isEqualTo(incident.id());
  }

  @Test
  void close_alreadyClosedIncident_throwsIncidentAlreadyClosedException() {
    Incident closed = newIncident(IncidentStatus.CLOSED);

    assertThatThrownBy(closed::close).isInstanceOf(IncidentAlreadyClosedException.class);
  }

  private static Incident newIncident(IncidentStatus status) {
    return new Incident(
        "incident-1",
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
