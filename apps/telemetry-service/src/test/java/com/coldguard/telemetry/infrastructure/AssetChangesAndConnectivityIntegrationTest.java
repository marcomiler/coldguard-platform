package com.coldguard.telemetry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.MessagingProperties;
import com.coldguard.commons.security.Actor;
import com.coldguard.telemetry.application.AssetChangeHandler;
import com.coldguard.telemetry.application.ConnectivityMonitor;
import com.coldguard.telemetry.application.IncomingReading;
import com.coldguard.telemetry.application.IngestReadingsService;
import com.coldguard.telemetry.application.SensorConditionRepository;
import com.coldguard.telemetry.domain.Criticality;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.EventContract;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What Asset announces, consumed from a real RabbitMQ into a real PostgreSQL, and the connectivity
 * check over the real SQL (locking included). The check is driven by hand and the outbox relay is
 * off, so the events a pass leaves are read straight from the outbox table.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "coldguard.outbox.enabled=false",
      "coldguard.telemetry.connectivity.enabled=false"
    })
class AssetChangesAndConnectivityIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "telemetry");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  private static final Actor SIMULATOR = Actor.system("sensor-simulator");
  private static final EvaluationProfile PROFILE =
      new EvaluationProfile(
          new BigDecimal("2.0"),
          new BigDecimal("8.0"),
          "CELSIUS",
          new BigDecimal("1.0"),
          new BigDecimal("3.0"),
          new BigDecimal("6.0"),
          3,
          Duration.ofMinutes(5),
          Duration.ofSeconds(5));

  @Autowired IngestReadingsService ingestion;
  @Autowired ConnectivityMonitor monitor;
  @Autowired SensorConditionRepository conditions;
  @Autowired AssetChangeHandler handler;
  @Autowired CachedSensorContexts contexts;
  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired MessagingProperties messaging;
  @Autowired EnvelopeCodec codec;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  @MockitoBean AssetContextClient asset;

  private final Map<UUID, SensorContext> known = new HashMap<>();

  @BeforeEach
  void stubAsset() {
    contexts.evictAll();
    known.clear();
    reset(asset);
    given(asset.fetch(anyCollection()))
        .willAnswer(
            call -> {
              Collection<UUID> ids = call.getArgument(0);
              List<SensorContext> found = new ArrayList<>();
              ids.forEach(
                  id -> {
                    if (known.containsKey(id)) {
                      found.add(known.get(id));
                    }
                  });
              return found;
            });
    jdbc.sql("DELETE FROM sensor_condition").update();
    jdbc.sql("DELETE FROM outbox_event").update();
  }

  private SensorContext reportingSensor() {
    SensorContext context =
        new SensorContext(
            UUID.randomUUID(), UUID.randomUUID(), Criticality.HIGH, SensorStatus.ACTIVE, PROFILE);
    known.put(context.sensorId(), context);
    ingestion.ingest(
        SIMULATOR,
        ReadingSource.SIMULATOR,
        List.of(
            new IncomingReading(
                UUID.randomUUID().toString(),
                context.sensorId().toString(),
                Instant.now().minusSeconds(1),
                5.0,
                "CELSIUS")));
    return context;
  }

  private void silentFor(UUID sensorId, long seconds) {
    jdbc.sql("UPDATE sensor_condition SET last_reading_at = ? WHERE sensor_id = ?")
        .params(java.sql.Timestamp.from(Instant.now().minusSeconds(seconds)), sensorId)
        .update();
  }

  private void publish(String eventType, String routingKey, Object payload, UUID eventId) {
    EventEnvelope envelope =
        new EventEnvelope(
            eventId,
            eventType,
            1,
            Instant.now(),
            "asset-service",
            "Sensor",
            UUID.randomUUID().toString(),
            null,
            "corr-it",
            EventActor.user("admin-it"),
            mapper.valueToTree(payload));
    Message message =
        MessageBuilder.withBody(codec.write(envelope).getBytes())
            .andProperties(persistentJson())
            .build();
    rabbitTemplate.send(messaging.exchange(), routingKey, message);
  }

  private static MessageProperties persistentJson() {
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    return properties;
  }

  private static void eventually(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met in time");
      }
      Thread.sleep(100);
    }
  }

  private String status(UUID sensorId) {
    return jdbc.sql("SELECT sensor_status FROM sensor_condition WHERE sensor_id = ?")
        .param(sensorId)
        .query(String.class)
        .single();
  }

  @Test
  void aStatusChangeFromAssetIsAppliedOnceAndTheNextReadingIsNotEvaluatedAsActive()
      throws Exception {
    SensorContext sensor = reportingSensor();
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload =
        Map.of(
            "sensorId", sensor.sensorId(),
            "assetId", sensor.assetId(),
            "previousStatus", "ACTIVE",
            "newStatus", "IN_MAINTENANCE",
            "reason", "calibration");

    publish("SensorStatusChanged", "asset.sensor-status-changed", payload, eventId);
    publish("SensorStatusChanged", "asset.sensor-status-changed", payload, eventId);

    eventually(() -> "IN_MAINTENANCE".equals(status(sensor.sensorId())));
    eventually(
        () ->
            jdbc.sql(
                        "SELECT count(*) FROM processed_message WHERE message_id = ?"
                            + " AND consumer = 'telemetry-service.asset-changes'")
                    .param(eventId)
                    .query(Long.class)
                    .single()
                == 1);
    // The duplicate left nothing behind and the sensor is not monitored while in maintenance.
    silentFor(sensor.sensorId(), 3600);
    assertThat(monitor.check()).isZero();
  }

  @Test
  void aProfileChangeUpdatesTheExpectedIntervalAndAReassignmentTheAsset() throws Exception {
    SensorContext sensor = reportingSensor();
    UUID newAsset = UUID.randomUUID();

    publish(
        "OperationalProfileUpdated",
        "asset.operational-profile-updated",
        Map.of(
            "sensorId", sensor.sensorId(),
            "profileVersion", 2,
            "previous", Map.of("expectedIntervalSeconds", 5),
            "current", Map.of("expectedIntervalSeconds", 120)),
        UUID.randomUUID());
    publish(
        "SensorReassigned",
        "asset.sensor-reassigned",
        Map.of(
            "sensorId",
            sensor.sensorId(),
            "previousAssetId",
            sensor.assetId(),
            "newAssetId",
            newAsset,
            "reason",
            "moved"),
        UUID.randomUUID());

    eventually(
        () ->
            jdbc.sql(
                        "SELECT count(*) FROM sensor_condition WHERE sensor_id = ?"
                            + " AND expected_interval_seconds = 120 AND asset_id = ?")
                    .params(sensor.sensorId(), newAsset)
                    .query(Long.class)
                    .single()
                == 1);
  }

  @Test
  void aMessageThatCanNeverBeProcessedEndsInTheDeadLetterQueue() throws Exception {
    rabbitTemplate.send(
        messaging.exchange(),
        "asset.sensor-status-changed",
        MessageBuilder.withBody("not json".getBytes()).andProperties(persistentJson()).build());
    publish(
        "SensorStatusChanged",
        "asset.sensor-status-changed",
        Map.of("sensorId", "not-a-uuid", "newStatus", "ACTIVE"),
        UUID.randomUUID());

    eventually(
        () -> {
          var info = rabbitTemplate.execute(ch -> ch.queueDeclarePassive(DLQ));
          return info.getMessageCount() >= 2;
        });
  }

  private static final String DLQ = "telemetry-service.asset-changes.dlq";

  @Test
  void aSilentSensorIsAnnouncedOnceAndTheLossIsClearedByTheNextReading() {
    SensorContext sensor = reportingSensor();
    silentFor(sensor.sensorId(), 60);

    assertThat(monitor.check()).isEqualTo(1);
    assertThat(monitor.check()).isZero();

    List<Map<String, Object>> events =
        jdbc.sql(
                "SELECT event_type, aggregate_id, payload::text AS payload FROM outbox_event"
                    + " WHERE event_type = 'SensorConnectivityLost'")
            .query()
            .listOfRows();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).get("aggregate_id")).isEqualTo(sensor.sensorId().toString());
    JsonNode envelope = mapper.readTree((String) events.get(0).get("payload"));
    EventContract.assertConforms("SensorConnectivityLost", 1, "Sensor", envelope.path("payload"));
    assertThat(envelope.path("actor").path("type").asString()).isEqualTo("SYSTEM");
    assertThat(envelope.path("actor").path("id").asString()).isEqualTo("connectivity-monitor");
    assertThat(
            jdbc.sql("SELECT connectivity_lost_at FROM sensor_condition WHERE sensor_id = ?")
                .param(sensor.sensorId())
                .query(java.time.OffsetDateTime.class)
                .single())
        .isNotNull();

    ingestion.ingest(
        SIMULATOR,
        ReadingSource.SIMULATOR,
        List.of(
            new IncomingReading(
                UUID.randomUUID().toString(),
                sensor.sensorId().toString(),
                Instant.now().minusSeconds(1),
                5.0,
                "CELSIUS")));

    assertThat(
            jdbc.sql("SELECT connectivity_lost_at FROM sensor_condition WHERE sensor_id = ?")
                .param(sensor.sensorId())
                .query(java.time.OffsetDateTime.class)
                .optional())
        .isEmpty();
  }

  @Test
  void aPassSkipsSensorsAnotherInstanceIsAlreadyHandling() throws Exception {
    SensorContext sensor = reportingSensor();
    silentFor(sensor.sensorId(), 60);
    var transaction = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService other = Executors.newSingleThreadExecutor();
    try {
      var holder =
          other.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        conditions.lockOverdue(Instant.now(), 1.5, 10);
                        locked.countDown();
                        try {
                          release.await(20, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();

      assertThat(monitor.check()).isZero();

      release.countDown();
      holder.get(20, TimeUnit.SECONDS);
      assertThat(monitor.check()).isEqualTo(1);
    } finally {
      release.countDown();
      other.shutdownNow();
    }
  }

  @Test
  void conditionsArePagedByIdWithTheirTotals() {
    for (int i = 0; i < 3; i++) {
      reportingSensor();
    }
    SensorContext silent = reportingSensor();
    silentFor(silent.sensorId(), 60);
    monitor.check();

    assertThat(conditions.count(false)).isEqualTo(4);
    assertThat(conditions.count(true)).isEqualTo(1);
    assertThat(conditions.findPage(false, 0, 3)).hasSize(3);
    assertThat(conditions.findPage(false, 1, 3)).hasSize(1);
    assertThat(conditions.findPage(true, 0, 10))
        .singleElement()
        .satisfies(c -> assertThat(c.sensorId()).isEqualTo(silent.sensorId()));
  }
}
