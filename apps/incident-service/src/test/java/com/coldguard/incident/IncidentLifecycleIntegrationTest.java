package com.coldguard.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.AcknowledgeIncidentCommand;
import com.coldguard.incident.application.AcknowledgeIncidentService;
import com.coldguard.incident.application.CloseIncidentCommand;
import com.coldguard.incident.application.CloseIncidentService;
import com.coldguard.incident.application.EscalateIncidentCommand;
import com.coldguard.incident.application.EscalateIncidentService;
import com.coldguard.incident.auditlog.application.ListAuditRecordsService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * The detection-to-closure flow against real PostgreSQL and RabbitMQ: a breach event opens one
 * incident, a redelivery or an equivalent breach never opens a second, the lifecycle commits its
 * events to the outbox, and a closed incident lets a new anomaly open a new one.
 */
@Testcontainers
@SpringBootTest
class IncidentLifecycleIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "incident");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  private static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));
  private static final Actor TECHNICIAN = new Actor("tech-1", Set.of(Role.MAINTENANCE_TECHNICIAN));

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired JdbcClient jdbc;
  @Autowired AcknowledgeIncidentService acknowledge;
  @Autowired EscalateIncidentService escalate;
  @Autowired CloseIncidentService close;
  @Autowired ListAuditRecordsService listAudit;
  @Autowired com.coldguard.incident.metrics.application.GetIncidentMetricsService metricsService;

  @Test
  void breachOpensOneIncident_redeliveryAndEquivalentBreachOnlyCount_lifecycleClosesIt() {
    UUID asset = UUID.randomUUID();
    UUID sensor = UUID.randomUUID();
    UUID firstEvent = UUID.randomUUID();

    send(firstEvent, asset, sensor, "HIGH", false);
    awaitIncidentCount(asset, 1);
    String incidentId = incidentId(asset);
    assertThat(column(incidentId, "status")).isEqualTo("CREATED");
    assertThat(column(incidentId, "priority")).isEqualTo("P2");
    assertThat(
            jdbc.sql("SELECT ack_due_at IS NOT NULL FROM incident.incident WHERE id = ?::uuid")
                .param(incidentId)
                .query(Boolean.class)
                .single())
        .isTrue();

    send(firstEvent, asset, sensor, "HIGH", false); // redelivery of the same message
    send(UUID.randomUUID(), asset, sensor, "HIGH", true); // equivalent, now persistent
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(column(incidentId, "occurrence_count")).isEqualTo("2"));
    assertThat(incidentCount(asset)).isEqualTo(1);
    assertThat(column(incidentId, "priority")).isEqualTo("P1");

    acknowledge.acknowledge(new AcknowledgeIncidentCommand(incidentId, SUPERVISOR));
    escalate.escalate(new EscalateIncidentCommand(incidentId, "no technician yet", SUPERVISOR));
    close.close(new CloseIncidentCommand(incidentId, "compressor failure", "replaced", TECHNICIAN));

    assertThat(column(incidentId, "status")).isEqualTo("CLOSED");
    assertThat(column(incidentId, "cause")).isEqualTo("compressor failure");
    assertThat(column(incidentId, "closed_by")).isEqualTo("tech-1");
    List<String> events =
        jdbc.sql(
                "SELECT event_type FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
            .param(incidentId)
            .query(String.class)
            .list();
    assertThat(events)
        .containsExactly(
            "IncidentCreated", "IncidentAcknowledged", "IncidentEscalated", "IncidentClosed");

    assertThat(
            jdbc.sql(
                    "SELECT action FROM auditlog.audit_record WHERE entity_type = 'Incident' AND entity_id = ?")
                .param(incidentId)
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder(
            "CREATED",
            "OCCURRENCE_REGISTERED",
            "PRIORITY_RECALCULATED",
            "ACKNOWLEDGED",
            "ESCALATED",
            "CLOSED");

    var metrics =
        metricsService.get(
            SUPERVISOR,
            java.time.Instant.now().minus(Duration.ofHours(1)),
            java.time.Instant.now().plus(Duration.ofHours(1)));
    assertThat(metrics.countByStatus().get(com.coldguard.incident.domain.IncidentStatus.CLOSED))
        .isGreaterThanOrEqualTo(1);
    assertThat(metrics.mttaSeconds()).isPresent();
    assertThat(metrics.mttrSeconds()).isPresent();
    var p1 = metrics.byPriority().get(com.coldguard.incident.domain.Priority.P1);
    assertThat(p1.closed()).isGreaterThanOrEqualTo(1);
    assertThat(p1.closeEvaluable()).isGreaterThanOrEqualTo(1);

    send(UUID.randomUUID(), asset, sensor, "HIGH", false);
    awaitIncidentCount(asset, 2);
  }

  @Test
  void auditsEventsFromOtherServices_onceEvenWhenRedelivered_andQueriesThemAsAuditor() {
    UUID eventId = UUID.randomUUID();
    String sensor = UUID.randomUUID().toString();
    String body =
        """
        {"eventId":"%s","eventType":"SensorStatusChanged","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"asset-service",
         "aggregateType":"Sensor","aggregateId":"%s","aggregateVersion":1,
         "actor":{"type":"USER","id":"admin-9"},
         "payload":{"sensorId":"%s","assetId":"%s","previousStatus":"ACTIVE",
           "newStatus":"IN_MAINTENANCE","reason":"drift"}}
        """
            .formatted(eventId, sensor, sensor, UUID.randomUUID());
    rabbitTemplate.send("coldguard.events", "asset.sensor-status-changed", message(body));
    rabbitTemplate.send("coldguard.events", "asset.sensor-status-changed", message(body));

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(auditRows(sensor)).isEqualTo(1));
    // give a possible duplicate time to show up
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(auditRows(sensor)).isEqualTo(1));

    var page =
        listAudit.list(
            new Actor("aud-1", Set.of(Role.AUDITOR)),
            new ListAuditRecordsService.Query(
                "Sensor",
                sensor,
                null,
                null,
                java.time.Instant.parse("2026-09-30T00:00:00Z"),
                java.time.Instant.parse("2026-10-02T00:00:00Z"),
                null,
                10));
    assertThat(page.records()).hasSize(1);
    assertThat(page.records().get(0).actorId()).isEqualTo("admin-9");
    assertThat(page.records().get(0).reason()).isEqualTo("drift");
    assertThat(page.records().get(0).newValue()).contains("IN_MAINTENANCE");
  }

  @Test
  void unmappedEventOnTheAuditQueue_isDeadLettered() {
    String body =
        """
        {"eventId":"%s","eventType":"MysteryHappened","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"asset-service",
         "aggregateType":"Asset","aggregateId":"x","actor":{"type":"USER","id":"u"},"payload":{}}
        """
            .formatted(UUID.randomUUID());
    rabbitTemplate.send("coldguard.events", "asset.asset-updated", message(body));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(rabbitTemplate.receive("incident-service.audit.dlq", 500)).isNotNull());
  }

  private int auditRows(String entityId) {
    return jdbc.sql("SELECT count(*) FROM auditlog.audit_record WHERE entity_id = ?")
        .param(entityId)
        .query(Integer.class)
        .single();
  }

  @Test
  void malformedPayload_isDeadLetteredNotRetriedForever() {
    String body =
        """
        {"eventId":"%s","eventType":"TelemetryThresholdBreached","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"telemetry-service",
         "aggregateType":"Sensor","aggregateId":"s","actor":{"type":"SYSTEM","id":"x"},
         "payload":{"magnitude":"NOT_A_MAGNITUDE"}}
        """
            .formatted(UUID.randomUUID());
    rabbitTemplate.send("coldguard.events", "telemetry.threshold-breached", message(body));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        rabbitTemplate.receive(
                            "incident-service.telemetry-threshold-breached.dlq", 500))
                    .isNotNull());
  }

  private void send(UUID eventId, UUID asset, UUID sensor, String magnitude, boolean persistent) {
    String body =
        """
        {"eventId":"%s","eventType":"TelemetryThresholdBreached","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"telemetry-service",
         "aggregateType":"Sensor","aggregateId":"%s","actor":{"type":"SYSTEM","id":"telemetry"},
         "payload":{"readingId":"%s","sensorId":"%s","assetId":"%s","assetCriticality":"HIGH",
           "anomalyType":"TEMPERATURE_ABOVE_MAX","value":12.0,"unit":"C","thresholdMin":2.0,
           "thresholdMax":8.0,"deviation":4.0,"magnitude":"%s","persistent":%s,
           "recordedAt":"2026-10-01T10:00:00Z"}}
        """
            .formatted(eventId, sensor, UUID.randomUUID(), sensor, asset, magnitude, persistent);
    rabbitTemplate.send("coldguard.events", "telemetry.threshold-breached", message(body));
  }

  private static Message message(String body) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    return MessageBuilder.withBody(body.getBytes()).andProperties(properties).build();
  }

  private void awaitIncidentCount(UUID asset, int expected) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(incidentCount(asset)).isEqualTo(expected));
  }

  private int incidentCount(UUID asset) {
    return jdbc.sql("SELECT count(*) FROM incident.incident WHERE asset_id = ?")
        .param(asset.toString())
        .query(Integer.class)
        .single();
  }

  private String incidentId(UUID asset) {
    return jdbc.sql(
            "SELECT id::text FROM incident.incident WHERE asset_id = ? ORDER BY created_at LIMIT 1")
        .param(asset.toString())
        .query(String.class)
        .single();
  }

  private String column(String incidentId, String column) {
    return jdbc.sql("SELECT " + column + "::text FROM incident.incident WHERE id = ?::uuid")
        .param(incidentId)
        .query(String.class)
        .single();
  }
}
