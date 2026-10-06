package com.coldguard.telemetry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.application.AssetUnavailableException;
import com.coldguard.telemetry.application.IncomingReading;
import com.coldguard.telemetry.application.IngestReadingsService;
import com.coldguard.telemetry.application.ReadingOutcome;
import com.coldguard.telemetry.application.ReadingQueryService;
import com.coldguard.telemetry.application.ReadingResult;
import com.coldguard.telemetry.domain.Criticality;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.EventContract;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ingestion against a real PostgreSQL: the migration, the SQL, idempotency under concurrency, the
 * per-sensor lock, the streak carried between requests, the events left in the outbox and what
 * happens while Asset is unavailable. Asset itself is stood in for by a mock of its client, so the
 * real cache sits in front of it.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "coldguard.outbox.enabled=false",
      "coldguard.telemetry.evaluation-context-cache.ttl=60s"
    })
class TelemetryPersistenceIntegrationTest {

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
  private static final Actor ADMIN = new Actor("admin-it", Set.of(Role.PLATFORM_ADMIN));
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
  @Autowired ReadingQueryService queries;
  @Autowired CachedSensorContexts contexts;
  @Autowired JdbcClient jdbc;
  @Autowired MeterRegistry meters;
  @Autowired ObjectMapper mapper;

  @MockitoBean AssetContextClient asset;

  /** What the mocked Asset knows. */
  private final Map<UUID, SensorContext> known = new HashMap<>();

  private RuntimeException assetFailure;

  @BeforeEach
  void stubAsset() {
    contexts.evictAll();
    known.clear();
    assetFailure = null;
    reset(asset);
    given(asset.fetch(anyCollection()))
        .willAnswer(
            call -> {
              if (assetFailure != null) {
                throw assetFailure;
              }
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
  }

  private SensorContext sensor(SensorStatus status, EvaluationProfile profile) {
    SensorContext context =
        new SensorContext(UUID.randomUUID(), UUID.randomUUID(), Criticality.HIGH, status, profile);
    known.put(context.sensorId(), context);
    return context;
  }

  private static IncomingReading reading(SensorContext sensor, double value, long secondsAgo) {
    return new IncomingReading(
        UUID.randomUUID().toString(),
        sensor.sensorId().toString(),
        Instant.now().minusSeconds(secondsAgo),
        value,
        "CELSIUS");
  }

  private List<ReadingResult> simulate(IncomingReading... readings) {
    return ingestion.ingest(SIMULATOR, ReadingSource.SIMULATOR, List.of(readings));
  }

  private long count(String sql, Object... params) {
    return jdbc.sql(sql).params(params).query(Long.class).single();
  }

  private long events(UUID sensorId) {
    return count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", sensorId.toString());
  }

  @Test
  void theMigrationCreatesTheTelemetryTables() {
    assertThat(
            jdbc.sql(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'telemetry'")
                .query(String.class)
                .list())
        .contains("telemetry_reading", "sensor_condition", "outbox_event", "processed_message");
  }

  @Test
  void readingsAreStoredAsEvidenceWithTheirEvaluationAndTheConditionIsKept() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    IncomingReading hot = reading(sensor, 12.345678, 5);
    CorrelationContext.set("corr-telemetry-1");
    try {
      simulate(reading(sensor, 5.0, 10), hot);
    } finally {
      CorrelationContext.clear();
    }

    var row =
        jdbc.sql("SELECT * FROM telemetry_reading WHERE id = ?")
            .param(UUID.fromString(hot.readingId()))
            .query()
            .singleRow();
    assertThat(row.get("sensor_id")).isEqualTo(sensor.sensorId());
    assertThat(row.get("asset_id")).isEqualTo(sensor.assetId());
    assertThat(((BigDecimal) row.get("value"))).isEqualByComparingTo("12.346");
    assertThat(row.get("unit")).isEqualTo("CELSIUS");
    assertThat(row.get("source")).isEqualTo("SIMULATOR");
    assertThat(row.get("eligible")).isEqualTo(true);
    assertThat(row.get("breached")).isEqualTo(true);
    assertThat(row.get("anomaly_type")).isEqualTo("TEMPERATURE_ABOVE_MAX");
    assertThat(row.get("magnitude")).isEqualTo("HIGH");
    assertThat(row.get("correlation_id")).isEqualTo("corr-telemetry-1");
    var condition =
        jdbc.sql("SELECT * FROM sensor_condition WHERE sensor_id = ?")
            .param(sensor.sensorId())
            .query()
            .singleRow();
    assertThat(condition.get("sensor_status")).isEqualTo("ACTIVE");
    assertThat(condition.get("expected_interval_seconds")).isEqualTo(5);
    assertThat(condition.get("breach_streak")).isEqualTo(1);
    assertThat(condition.get("breach_anomaly_type")).isEqualTo("TEMPERATURE_ABOVE_MAX");
    assertThat(condition.get("connectivity_lost_at")).isNull();
  }

  @Test
  void aBreachLeavesAnEventThatFollowsItsContractAndEachOneContinuesTheSensorSequence()
      throws Exception {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);

    simulate(reading(sensor, 9.0, 30));
    simulate(reading(sensor, 9.5, 20));
    ingestion.ingest(ADMIN, ReadingSource.TEST_INJECTION, List.of(reading(sensor, 10.0, 10)));

    var rows =
        jdbc.sql(
                "SELECT event_type, routing_key, aggregate_type, aggregate_version, event_version, payload::text AS payload"
                    + " FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
            .param(sensor.sensorId().toString())
            .query()
            .listOfRows();
    assertThat(rows).hasSize(3);
    assertThat(rows.stream().map(r -> ((Number) r.get("aggregate_version")).longValue()))
        .containsExactly(1L, 2L, 3L);
    List<Boolean> persistent = new ArrayList<>();
    for (var row : rows) {
      assertThat(row.get("event_type")).isEqualTo("TelemetryThresholdBreached");
      assertThat(row.get("routing_key")).isEqualTo("telemetry.threshold-breached");
      JsonNode envelope = mapper.readTree((String) row.get("payload"));
      assertThat(envelope.get("producer").asString()).isEqualTo("telemetry-service");
      EventContract.assertConforms(
          (String) row.get("event_type"),
          ((Number) row.get("event_version")).intValue(),
          (String) row.get("aggregate_type"),
          envelope.get("payload"));
      persistent.add(envelope.get("payload").get("persistent").asBoolean());
    }
    assertThat(persistent).containsExactly(false, false, true);
    JsonNode last = mapper.readTree((String) rows.get(2).get("payload"));
    assertThat(last.get("actor").get("type").asString()).isEqualTo("USER");
    assertThat(last.get("actor").get("id").asString()).isEqualTo("admin-it");
  }

  @Test
  void theStreakSurvivesBetweenRequestsAndARecoveryEndsIt() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);

    simulate(reading(sensor, 9.0, 40));
    simulate(reading(sensor, 9.0, 30));
    simulate(reading(sensor, 5.0, 20));
    simulate(reading(sensor, 9.0, 10));

    assertThat(
            jdbc.sql(
                    "SELECT payload->'payload'->>'persistent' FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
                .param(sensor.sensorId().toString())
                .query(String.class)
                .list())
        .containsExactly("false", "false", "false");
    assertThat(
            count(
                "SELECT breach_streak FROM sensor_condition WHERE sensor_id = ?",
                sensor.sensorId()))
        .isEqualTo(1);
  }

  @Test
  void readingsOfASensorThatIsNotActiveAreKeptButRaiseNothing() {
    for (SensorStatus status :
        new SensorStatus[] {
          SensorStatus.IN_MAINTENANCE, SensorStatus.INACTIVE, SensorStatus.RETIRED
        }) {
      SensorContext sensor = sensor(status, PROFILE);
      IncomingReading hot = reading(sensor, 50.0, 5);

      var result = simulate(hot).get(0);

      assertThat(result.eligible()).as(status.name()).isFalse();
      assertThat(result.breached()).isFalse();
      assertThat(
              jdbc.sql(
                      "SELECT eligible || '/' || ineligibility_reason || '/' || breached FROM telemetry_reading WHERE id = ?")
                  .param(UUID.fromString(hot.readingId()))
                  .query(String.class)
                  .single())
          .isEqualTo("false/SENSOR_NOT_ACTIVE/false");
      assertThat(events(sensor.sensorId())).isZero();
    }
  }

  @Test
  void aSensorWithoutAProfileIsKeptWithItsReason() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, null);
    IncomingReading hot = reading(sensor, 50.0, 5);

    simulate(hot);

    assertThat(
            jdbc.sql("SELECT ineligibility_reason FROM telemetry_reading WHERE id = ?")
                .param(UUID.fromString(hot.readingId()))
                .query(String.class)
                .single())
        .isEqualTo("NO_PROFILE");
    assertThat(
            count(
                "SELECT expected_interval_seconds FROM sensor_condition WHERE sensor_id = ?",
                sensor.sensorId()))
        .isZero();
  }

  @Test
  void resendingABatchCreatesNothingNewAndDuplicatesAreReported() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    IncomingReading hot = reading(sensor, 12.0, 5);
    IncomingReading fine = reading(sensor, 5.0, 4);
    simulate(hot, fine);
    long events = events(sensor.sensorId());

    var again = simulate(hot, fine);

    assertThat(again).extracting(ReadingResult::outcome).containsOnly(ReadingOutcome.DUPLICATE);
    assertThat(events(sensor.sensorId())).isEqualTo(events);
    assertThat(
            count("SELECT count(*) FROM telemetry_reading WHERE sensor_id = ?", sensor.sensorId()))
        .isEqualTo(2);
  }

  @Test
  void twoRequestsSendingTheSameBatchAtOnceStoreItAndAnnounceItExactlyOnce() throws Exception {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    List<IncomingReading> batch = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      batch.add(reading(sensor, 12.0, 100 - i));
    }
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<List<ReadingResult>>> runs = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        runs.add(
            pool.submit(
                () -> {
                  start.await();
                  return ingestion.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);
                }));
      }
      start.countDown();
      long accepted = 0;
      long duplicates = 0;
      for (var run : runs) {
        for (ReadingResult result : run.get(30, TimeUnit.SECONDS)) {
          accepted += result.outcome() == ReadingOutcome.ACCEPTED ? 1 : 0;
          duplicates += result.outcome() == ReadingOutcome.DUPLICATE ? 1 : 0;
        }
      }
      assertThat(accepted).isEqualTo(6);
      assertThat(duplicates).isEqualTo(6);
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            count("SELECT count(*) FROM telemetry_reading WHERE sensor_id = ?", sensor.sensorId()))
        .isEqualTo(6);
    assertThat(events(sensor.sensorId())).isEqualTo(6);
    assertThat(
            count(
                "SELECT breach_streak FROM sensor_condition WHERE sensor_id = ?",
                sensor.sensorId()))
        .isEqualTo(6);
  }

  @Test
  void batchesThatReachTheSameSensorsInOppositeOrderDoNotDeadlock() throws Exception {
    SensorContext a = sensor(SensorStatus.ACTIVE, PROFILE);
    SensorContext b = sensor(SensorStatus.ACTIVE, PROFILE);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<?>> runs = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        boolean aFirst = i % 2 == 0;
        runs.add(
            pool.submit(
                () -> {
                  start.await();
                  for (int round = 0; round < 5; round++) {
                    List<IncomingReading> batch =
                        aFirst
                            ? List.of(reading(a, 9.0, 50), reading(b, 9.0, 50))
                            : List.of(reading(b, 9.0, 50), reading(a, 9.0, 50));
                    ingestion.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> run : runs) {
        run.get(60, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(count("SELECT count(*) FROM telemetry_reading WHERE sensor_id = ?", a.sensorId()))
        .isEqualTo(40);
    assertThat(count("SELECT count(*) FROM telemetry_reading WHERE sensor_id = ?", b.sensorId()))
        .isEqualTo(40);
  }

  @Test
  void aSensorUnknownToAssetIsRejectedAndNothingIsStoredForIt() {
    UUID unknown = UUID.randomUUID();
    IncomingReading reading =
        new IncomingReading(
            UUID.randomUUID().toString(), unknown.toString(), Instant.now(), 5, "CELSIUS");

    var result = simulate(reading).get(0);

    assertThat(result.outcome()).isEqualTo(ReadingOutcome.REJECTED);
    assertThat(result.rejectionCode()).isEqualTo("SENSOR_NOT_FOUND");
    assertThat(count("SELECT count(*) FROM telemetry_reading WHERE sensor_id = ?", unknown))
        .isZero();
    assertThat(count("SELECT count(*) FROM sensor_condition WHERE sensor_id = ?", unknown))
        .isZero();
  }

  @Test
  void whileAssetIsDownAnUncachedSensorFailsTheRequestAndACachedOneIsStillServed() {
    SensorContext cached = sensor(SensorStatus.ACTIVE, PROFILE);
    SensorContext uncached = sensor(SensorStatus.ACTIVE, PROFILE);
    simulate(reading(cached, 5.0, 10));
    assetFailure = new AssetUnavailableException("down", null);
    IncomingReading hot = reading(uncached, 50.0, 5);

    assertThatThrownBy(() -> simulate(hot)).isInstanceOf(AssetUnavailableException.class);
    assertThat(
            count(
                "SELECT count(*) FROM telemetry_reading WHERE id = ?",
                UUID.fromString(hot.readingId())))
        .isZero();

    assertThat(simulate(reading(cached, 5.0, 5)).get(0).outcome())
        .isEqualTo(ReadingOutcome.ACCEPTED);
    assetFailure = null;
    assertThat(simulate(hot).get(0).outcome())
        .as("the producer retries and it works")
        .isEqualTo(ReadingOutcome.ACCEPTED);
  }

  @Test
  void assetIsNotAskedAgainForACachedSensor() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);

    simulate(reading(sensor, 5.0, 30));
    simulate(reading(sensor, 5.0, 20));
    simulate(reading(sensor, 5.0, 10));

    verify(asset, times(1)).fetch(anyCollection());
  }

  @Test
  void aStatusChangeIsSeenOnceTheCachedContextIsEvicted() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    simulate(reading(sensor, 5.0, 30));
    known.put(
        sensor.sensorId(),
        new SensorContext(
            sensor.sensorId(),
            sensor.assetId(),
            sensor.assetCriticality(),
            SensorStatus.IN_MAINTENANCE,
            PROFILE));

    var stale = simulate(reading(sensor, 50.0, 20)).get(0);
    contexts.evict(sensor.sensorId());
    var fresh = simulate(reading(sensor, 50.0, 10)).get(0);

    assertThat(stale.eligible())
        .as("until the TTL or an eviction, the cached context applies")
        .isTrue();
    assertThat(fresh.eligible()).isFalse();
    assertThat(
            jdbc.sql("SELECT sensor_status FROM sensor_condition WHERE sensor_id = ?")
                .param(sensor.sensorId())
                .query(String.class)
                .single())
        .isEqualTo("IN_MAINTENANCE");
  }

  @Test
  void readingsAreListedFromTheDatabaseNewestFirstWithACursorEvenWhenTimestampsTie() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    Instant same =
        Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    List<IncomingReading> batch = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      batch.add(
          new IncomingReading(
              UUID.randomUUID().toString(), sensor.sensorId().toString(), same, 5, "CELSIUS"));
    }
    batch.add(reading(sensor, 5.0, 60));
    batch.add(reading(sensor, 5.0, 30));
    simulate(batch.toArray(IncomingReading[]::new));

    List<UUID> expected =
        jdbc.sql(
                "SELECT id FROM telemetry_reading WHERE sensor_id = ? ORDER BY recorded_at DESC, id DESC")
            .param(sensor.sensorId())
            .query(UUID.class)
            .list();
    List<UUID> walked = new ArrayList<>();
    String cursor = "";
    Set<String> cursors = new HashSet<>();
    do {
      var page =
          queries.list(
              ADMIN,
              sensor.sensorId(),
              Instant.now().minusSeconds(3600),
              Instant.now().plusSeconds(60),
              cursor,
              2);
      page.readings().forEach(r -> walked.add(r.id()));
      cursor = page.nextCursor();
      assertThat(cursors.add(cursor) || cursor.isEmpty()).isTrue();
    } while (!cursor.isEmpty());

    assertThat(expected).hasSize(6);
    assertThat(walked).containsExactlyElementsOf(expected);
  }

  @Test
  void everyReadingFeedsTheReadingsMetric() {
    SensorContext sensor = sensor(SensorStatus.ACTIVE, PROFILE);
    var counter =
        meters.counter(
            "coldguard.telemetry.readings",
            "source",
            "SIMULATOR",
            "outcome",
            "ACCEPTED",
            "eligible",
            "true",
            "breached",
            "true");
    double before = counter.count();

    simulate(reading(sensor, 12.0, 5));

    assertThat(counter.count()).isEqualTo(before + 1);
  }
}
