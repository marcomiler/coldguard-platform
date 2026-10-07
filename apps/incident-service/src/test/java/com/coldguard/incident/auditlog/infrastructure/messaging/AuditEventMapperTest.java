package com.coldguard.incident.auditlog.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.incident.auditlog.application.AuditEntry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuditEventMapperTest {

  private static final Instant AT = Instant.parse("2026-10-01T10:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final AuditEventMapper mapper = new AuditEventMapper();

  private static EventEnvelope event(
      String type, String aggregate, EventActor actor, String payload) {
    return new EventEnvelope(
        UUID.randomUUID(),
        type,
        1,
        AT,
        "asset-service",
        aggregate,
        "agg-1",
        1L,
        "corr-1",
        actor,
        JSON.readTree(payload));
  }

  @Test
  void sensorStatusChange_keepsActorReasonAndBothValues() {
    AuditEntry entry =
        mapper.map(
            event(
                "SensorStatusChanged",
                "Sensor",
                EventActor.user("admin-1"),
                "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"IN_MAINTENANCE\",\"reason\":\"drift\"}"));

    assertThat(entry.action()).isEqualTo("SENSOR_STATUS_CHANGED");
    assertThat(entry.entityType()).isEqualTo("Sensor");
    assertThat(entry.entityId()).isEqualTo("agg-1");
    assertThat(entry.actorId()).isEqualTo("admin-1");
    assertThat(entry.actorType()).isEqualTo(AuditEntry.ActorType.USER);
    assertThat(entry.reason()).isEqualTo("drift");
    assertThat(entry.before()).contains("ACTIVE");
    assertThat(entry.after()).contains("IN_MAINTENANCE");
    assertThat(entry.sourceService()).isEqualTo("asset-service");
    assertThat(entry.correlationId()).isEqualTo("corr-1");
    assertThat(entry.occurredAt()).isEqualTo(AT);
    assertThat(entry.sourceEventId()).isNotNull();
  }

  @Test
  void systemActorIsKeptAsSystem() {
    AuditEntry entry =
        mapper.map(
            event(
                "SensorConnectivityLost",
                "Sensor",
                EventActor.system("connectivity-monitor"),
                "{\"sensorId\":\"s\",\"assetId\":\"a\",\"lastReadingAt\":\"2026-10-01T09:00:00Z\","
                    + "\"expectedIntervalSeconds\":5,\"detectedAt\":\"2026-10-01T10:00:00Z\"}"));

    assertThat(entry.action()).isEqualTo("SENSOR_CONNECTIVITY_LOST");
    assertThat(entry.actorType()).isEqualTo(AuditEntry.ActorType.SYSTEM);
    assertThat(entry.actorId()).isEqualTo("connectivity-monitor");
  }

  @Test
  void assetUpdate_usesPreviousAndCurrentDocuments() {
    AuditEntry entry =
        mapper.map(
            event(
                "AssetUpdated",
                "Asset",
                EventActor.user("admin-1"),
                "{\"assetId\":\"a\",\"changedFields\":[\"criticality\"],"
                    + "\"previous\":{\"criticality\":\"LOW\"},\"current\":{\"criticality\":\"HIGH\"}}"));

    assertThat(entry.before()).isEqualTo("{\"criticality\":\"LOW\"}");
    assertThat(entry.after()).isEqualTo("{\"criticality\":\"HIGH\"}");
  }

  @Test
  void everySupportedEventTypeHasAMapping() {
    for (String type : AuditEventMapper.SUPPORTED.keySet()) {
      AuditEntry entry = mapper.map(event(type, "X", EventActor.user("u"), "{}"));
      assertThat(entry.action()).isNotBlank();
    }
  }

  @Test
  void unknownEventIsRejectedNotGuessed() {
    assertThatThrownBy(() -> mapper.map(event("SomethingNew", "X", EventActor.user("u"), "{}")))
        .isInstanceOf(PermanentMessageException.class);
  }
}
