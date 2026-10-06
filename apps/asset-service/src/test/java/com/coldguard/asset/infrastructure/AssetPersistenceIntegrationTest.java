package com.coldguard.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.application.AssetCatalogService;
import com.coldguard.asset.application.CalibrationExpiryService;
import com.coldguard.asset.application.DueSensor;
import com.coldguard.asset.application.EvaluationContextService;
import com.coldguard.asset.application.OperationalProfileDraft;
import com.coldguard.asset.application.OperationalProfileService;
import com.coldguard.asset.application.SensorHistoryService;
import com.coldguard.asset.application.SensorLifecycleService;
import com.coldguard.asset.application.SensorRepository;
import com.coldguard.asset.application.SensorService;
import com.coldguard.asset.application.SensorService.InitialCalibration;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.Site;
import com.coldguard.asset.domain.StaleVersionException;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * The use cases against a real PostgreSQL: the migration, the SQL of every repository, uniqueness,
 * optimistic locking, the rollback of a failed registration and the events left in the outbox. The
 * relay is switched off so the pending rows stay where the test can read them.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "coldguard.outbox.enabled=false",
      "coldguard.asset.calibration-expiry.enabled=false",
      "coldguard.asset.calibration.default-validity=P90D"
    })
class AssetPersistenceIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "asset");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  private static final Actor ADMIN = new Actor("admin-it", Set.of(Role.PLATFORM_ADMIN));

  @Autowired AssetCatalogService catalog;
  @Autowired SensorService sensors;
  @Autowired OperationalProfileService profiles;
  @Autowired SensorLifecycleService lifecycle;
  @Autowired CalibrationExpiryService expiry;
  @Autowired SensorRepository sensorRepository;
  @Autowired SensorHistoryService history;
  @Autowired EvaluationContextService contexts;
  @Autowired JdbcClient jdbc;

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Asset anAsset() {
    Organization org = catalog.createOrganization(ADMIN, unique("org"));
    Site site = catalog.createSite(ADMIN, org.id(), "Main", "1 Cold Street");
    return catalog.registerAsset(ADMIN, site.id(), unique("room"), "Vaccines", Criticality.HIGH);
  }

  private static OperationalProfileDraft draft(String unit, Duration validity) {
    return new OperationalProfileDraft(
        UUID.randomUUID(),
        new BigDecimal("2.0"),
        new BigDecimal("8.0"),
        unit,
        new BigDecimal("1.0"),
        new BigDecimal("3.0"),
        new BigDecimal("6.0"),
        3,
        Duration.ofMinutes(5),
        Duration.ofSeconds(5),
        validity);
  }

  private long count(String table) {
    return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  private long outboxFor(String aggregateId) {
    return jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
        .param(aggregateId)
        .query(Long.class)
        .single();
  }

  @Test
  void migrationCreatesTheAssetTablesInTheServiceSchema() {
    List<String> tables =
        jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'asset'")
            .query(String.class)
            .list();

    assertThat(tables)
        .contains(
            "organization",
            "site",
            "asset",
            "sensor",
            "operational_profile",
            "calibration_record",
            "sensor_assignment_history",
            "sensor_lifecycle_audit",
            "outbox_event");
  }

  @Test
  void theWholeChainIsStoredAndReadBack() {
    Asset asset = anAsset();
    Instant performedAt = Instant.now().minusSeconds(120);

    Sensor sensor =
        sensors.register(
            ADMIN,
            asset.id(),
            unique("SN"),
            "Model X",
            "CELSIUS",
            new InitialCalibration(CalibrationKind.CALIBRATION, performedAt, "Factory"),
            draft("CELSIUS", Duration.ofDays(30)));

    Sensor read = sensors.get(ADMIN, sensor.id());
    assertThat(read.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(read.assetId()).isEqualTo(asset.id());
    assertThat(read.lastCalibrationValidUntil())
        .isCloseTo(
            performedAt.plus(Duration.ofDays(30)),
            org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    assertThat(read.version()).isEqualTo(1);

    var profile = profiles.get(ADMIN, sensor.id());
    assertThat(profile.version()).isEqualTo(1);
    assertThat(profile.minTemperature()).isEqualByComparingTo("2.00");
    assertThat(profile.calibrationValidity()).isEqualTo(Duration.ofDays(30));
    assertThat(profile.persistenceWindow()).isEqualTo(Duration.ofMinutes(5));

    assertThat(
            jdbc.sql(
                    "SELECT action FROM sensor_lifecycle_audit WHERE sensor_id = ? ORDER BY occurred_at, action")
                .param(sensor.id())
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder("REGISTERED", "CALIBRATION_RECORDED", "PROFILE_UPDATED");
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM sensor_assignment_history WHERE sensor_id = ? AND previous_asset_id IS NULL AND assigned_by = 'admin-it'")
                .param(sensor.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT count(*) FROM calibration_record WHERE sensor_id = ?")
                .param(sensor.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void eachWriteLeavesItsEventInTheOutboxWithAPerAggregateSequence() {
    Asset asset = anAsset();
    catalog.updateAsset(ADMIN, asset.id(), 1, "Renamed " + UUID.randomUUID(), null, null);
    Sensor sensor =
        sensors.register(
            ADMIN,
            asset.id(),
            unique("SN"),
            null,
            "CELSIUS",
            new InitialCalibration(
                CalibrationKind.VERIFICATION, Instant.now().minusSeconds(5), "Check"),
            draft("CELSIUS", null));

    List<Map<String, Object>> assetEvents =
        jdbc.sql(
                "SELECT event_type, routing_key, aggregate_version, payload->>'producer' AS producer,"
                    + " payload->'actor'->>'id' AS actor FROM outbox_event WHERE aggregate_id = ?"
                    + " ORDER BY aggregate_version")
            .param(asset.id().toString())
            .query()
            .listOfRows();
    assertThat(assetEvents)
        .extracting(
            r -> r.get("event_type"),
            r -> r.get("routing_key"),
            r -> ((Number) r.get("aggregate_version")).longValue())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("AssetRegistered", "asset.asset-registered", 1L),
            org.assertj.core.groups.Tuple.tuple("AssetUpdated", "asset.asset-updated", 2L));
    assertThat(assetEvents)
        .allSatisfy(
            r -> {
              assertThat(r.get("producer")).isEqualTo("asset-service");
              assertThat(r.get("actor")).isEqualTo("admin-it");
            });

    assertThat(
            jdbc.sql(
                    "SELECT event_type FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
                .param(sensor.id().toString())
                .query(String.class)
                .list())
        .containsExactly("SensorCalibrationRecorded", "OperationalProfileUpdated");
  }

  @Test
  void aFailedRegistrationRollsBackEverythingItWrote() {
    Asset asset = anAsset();
    long sensorsBefore = count("sensor");
    long assignmentsBefore = count("sensor_assignment_history");
    long auditBefore = count("sensor_lifecycle_audit");
    long outboxBefore = count("outbox_event");
    long calibrationsBefore = count("calibration_record");

    assertThatThrownBy(
            () ->
                sensors.register(
                    ADMIN,
                    asset.id(),
                    unique("SN"),
                    null,
                    "CELSIUS",
                    new InitialCalibration(
                        CalibrationKind.CALIBRATION, Instant.now().minusSeconds(5), "x"),
                    draft("FAHRENHEIT", null)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(count("sensor")).isEqualTo(sensorsBefore);
    assertThat(count("sensor_assignment_history")).isEqualTo(assignmentsBefore);
    assertThat(count("sensor_lifecycle_audit")).isEqualTo(auditBefore);
    assertThat(count("calibration_record")).isEqualTo(calibrationsBefore);
    assertThat(count("outbox_event")).isEqualTo(outboxBefore);
  }

  @Test
  void namesAndSerialsAreUniqueIgnoringCase() {
    String orgName = unique("Org");
    Organization org = catalog.createOrganization(ADMIN, orgName);
    catalog.createSite(ADMIN, org.id(), "Main", null);
    Asset asset = anAsset();
    String serial = unique("SN");
    sensors.register(ADMIN, asset.id(), serial, null, "CELSIUS", null, null);

    assertThatThrownBy(() -> catalog.createOrganization(ADMIN, orgName.toUpperCase()))
        .isInstanceOfSatisfying(
            AlreadyExistsException.class,
            e -> assertThat(e.code()).isEqualTo("ORGANIZATION_NAME_DUPLICATED"));
    assertThatThrownBy(() -> catalog.createSite(ADMIN, org.id(), "MAIN", null))
        .isInstanceOfSatisfying(
            AlreadyExistsException.class,
            e -> assertThat(e.code()).isEqualTo("SITE_NAME_DUPLICATED"));
    assertThatThrownBy(
            () ->
                sensors.register(
                    ADMIN, asset.id(), serial.toLowerCase(), null, "CELSIUS", null, null))
        .isInstanceOfSatisfying(
            AlreadyExistsException.class,
            e -> assertThat(e.code()).isEqualTo("SENSOR_SERIAL_DUPLICATED"));
    Organization other = catalog.createOrganization(ADMIN, unique("Other"));
    assertThat(catalog.createSite(ADMIN, other.id(), "Main", null)).isNotNull();
  }

  @Test
  void concurrentModificationIsDetectedForAssetsSensorsAndProfiles() {
    Asset asset = anAsset();
    catalog.updateAsset(ADMIN, asset.id(), 1, "First " + UUID.randomUUID(), null, null);
    assertThatThrownBy(
            () ->
                catalog.updateAsset(
                    ADMIN, asset.id(), 1, "Second " + UUID.randomUUID(), null, null))
        .isInstanceOf(StaleVersionException.class);

    Sensor sensor = sensors.register(ADMIN, asset.id(), unique("SN"), "A", "CELSIUS", null, null);
    sensors.update(ADMIN, sensor.id(), 1, null, "B");
    assertThatThrownBy(() -> sensors.update(ADMIN, sensor.id(), 1, null, "C"))
        .isInstanceOf(StaleVersionException.class);

    profiles.upsert(ADMIN, draft("CELSIUS", null).forSensor(sensor.id()), 0);
    assertThatThrownBy(
            () ->
                profiles.upsert(
                    ADMIN, draft("CELSIUS", Duration.ofDays(1)).forSensor(sensor.id()), 0))
        .isInstanceOf(StaleVersionException.class);
    assertThat(
            profiles
                .upsert(ADMIN, draft("CELSIUS", Duration.ofDays(1)).forSensor(sensor.id()), 1)
                .version())
        .isEqualTo(2);
  }

  @Test
  void listingFiltersAndPagesInTheDatabase() {
    Asset a = anAsset();
    Asset b = anAsset();
    for (int i = 0; i < 3; i++) {
      sensors.register(ADMIN, a.id(), unique("SN"), null, "CELSIUS", null, null);
    }
    sensors.register(ADMIN, b.id(), unique("SN"), null, "CELSIUS", null, null);

    assertThat(sensors.list(ADMIN, a.id(), null, 0, 2).items()).hasSize(2);
    assertThat(sensors.list(ADMIN, a.id(), null, 1, 2).items()).hasSize(1);
    assertThat(sensors.list(ADMIN, a.id(), null, 0, 2).totalElements()).isEqualTo(3);
    assertThat(sensors.list(ADMIN, b.id(), SensorStatus.ACTIVE, 0, 10).items()).hasSize(1);
    assertThat(sensors.list(ADMIN, b.id(), SensorStatus.RETIRED, 0, 10).items()).isEmpty();
    assertThat(sensors.list(ADMIN, null, null, 0, 100).totalElements()).isGreaterThanOrEqualTo(4);
    assertThat(catalog.listAssets(ADMIN, a.siteId(), 0, 10).items())
        .extracting(Asset::id)
        .containsExactly(a.id());
    assertThat(catalog.listSites(ADMIN, null, 0, 100).totalElements()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void theDatabaseItselfRefusesAnInvalidProfile() {
    Asset asset = anAsset();
    Sensor sensor = sensors.register(ADMIN, asset.id(), unique("SN"), null, "CELSIUS", null, null);

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO operational_profile
                          (sensor_id, min_temperature, max_temperature, unit, magnitude_medium_from,
                           magnitude_high_from, magnitude_critical_from, persistence_min_consecutive,
                           persistence_window_seconds, expected_interval_seconds, updated_at,
                           updated_by, version)
                        VALUES (?, 9, 8, 'CELSIUS', 1, 3, 6, 3, 300, 5, now(), 'x', 1)
                        """)
                    .param(sensor.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anEmptyUpdateStaysAtTheSameVersionAndPublishesNothing() {
    Asset asset = anAsset();
    long before = outboxFor(asset.id().toString());

    Asset same = catalog.updateAsset(ADMIN, asset.id(), 1, asset.name(), null, null);

    assertThat(same.version()).isEqualTo(1);
    assertThat(outboxFor(asset.id().toString())).isEqualTo(before);
  }

  // ---- lifecycle -------------------------------------------------------------------------

  private static void pause() throws InterruptedException {
    Thread.sleep(15);
  }

  private Sensor registered(Asset asset) {
    return sensors.register(
        ADMIN,
        asset.id(),
        unique("SN"),
        null,
        "CELSIUS",
        new InitialCalibration(
            CalibrationKind.CALIBRATION, Instant.now().minusSeconds(30), "Factory"),
        draft("CELSIUS", null));
  }

  @Test
  void aFullLifecycleIsStoredWithItsHistoryAndAnOrderedEventStream() throws Exception {
    Asset first = anAsset();
    Asset second = anAsset();
    Sensor sensor = registered(first);

    pause();
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "drifting");
    pause();
    lifecycle.recordCalibration(
        ADMIN,
        sensor.id(),
        CalibrationKind.CALIBRATION,
        Instant.now().minusSeconds(5),
        "Recalibrated");
    assertThat(sensors.get(ADMIN, sensor.id()).status())
        .as("a calibration alone does not reactivate")
        .isEqualTo(SensorStatus.IN_MAINTENANCE);
    pause();
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "back in service");
    pause();
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "relocation");
    lifecycle.reassign(ADMIN, sensor.id(), second.id(), "new cold room");
    lifecycle.retire(ADMIN, sensor.id(), "end of life");

    Sensor read = sensors.get(ADMIN, sensor.id());
    assertThat(read.status()).isEqualTo(SensorStatus.RETIRED);
    assertThat(read.assetId()).isEqualTo(second.id());
    assertThat(
            jdbc.sql("SELECT count(*) FROM calibration_record WHERE sensor_id = ?")
                .param(sensor.id())
                .query(Long.class)
                .single())
        .isEqualTo(2);
    assertThat(
            jdbc.sql(
                    "SELECT asset_id, previous_asset_id FROM sensor_assignment_history WHERE sensor_id = ? ORDER BY assigned_at")
                .param(sensor.id())
                .query()
                .listOfRows())
        .extracting(r -> r.get("asset_id"), r -> r.get("previous_asset_id"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(first.id(), null),
            org.assertj.core.groups.Tuple.tuple(second.id(), first.id()));
    assertThat(
            jdbc.sql("SELECT DISTINCT action FROM sensor_lifecycle_audit WHERE sensor_id = ?")
                .param(sensor.id())
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder(
            "REGISTERED",
            "CALIBRATION_RECORDED",
            "PROFILE_UPDATED",
            "STATUS_CHANGED",
            "REASSIGNED",
            "RETIRED");

    List<Map<String, Object>> events =
        jdbc.sql(
                "SELECT event_type, aggregate_version FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
            .param(sensor.id().toString())
            .query()
            .listOfRows();
    assertThat(events.stream().map(r -> r.get("event_type")))
        .containsExactly(
            "SensorCalibrationRecorded",
            "OperationalProfileUpdated",
            "SensorStatusChanged",
            "SensorCalibrationRecorded",
            "SensorStatusChanged",
            "SensorStatusChanged",
            "SensorReassigned",
            "SensorRetired",
            "SensorStatusChanged");
    assertThat(events.stream().map(r -> ((Number) r.get("aggregate_version")).longValue()))
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

    long audits = count("sensor_lifecycle_audit");
    long outbox = count("outbox_event");
    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "revive"))
        .isInstanceOf(com.coldguard.asset.domain.SensorTransitionNotAllowedException.class);
    assertThat(count("sensor_lifecycle_audit"))
        .as("a rejected change leaves no trace")
        .isEqualTo(audits);
    assertThat(count("outbox_event")).isEqualTo(outbox);
    assertThat(history.history(ADMIN, sensor.id(), "", 100).items()).isNotEmpty();
  }

  @Test
  void theHistoryCursorWalksRealRowsIncludingEntriesWrittenInTheSameInstant() throws Exception {
    Sensor sensor = registered(anAsset());
    pause();
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "off");
    pause();
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "inspect");

    List<String> expected =
        jdbc.sql(
                "SELECT id::text FROM sensor_lifecycle_audit WHERE sensor_id = ? ORDER BY occurred_at DESC, id DESC")
            .param(sensor.id())
            .query(String.class)
            .list();
    assertThat(expected).hasSize(5);
    assertThat(
            jdbc.sql(
                    "SELECT count(DISTINCT occurred_at) FROM sensor_lifecycle_audit WHERE sensor_id = ?")
                .param(sensor.id())
                .query(Long.class)
                .single())
        .as("registration writes three entries at the same instant")
        .isLessThan(5);

    List<String> walked = new java.util.ArrayList<>();
    String cursor = "";
    do {
      var page = history.history(ADMIN, sensor.id(), cursor, 2);
      page.items().forEach(e -> walked.add(e.id().toString()));
      cursor = page.nextCursor();
    } while (!cursor.isEmpty());

    assertThat(walked).containsExactlyElementsOf(expected);
  }

  @Test
  void theEvaluationContextIsReadWithOneJoinIncludingTheArrayParameter() {
    Asset asset = anAsset();
    Sensor withProfile = registered(asset);
    Sensor bare = sensors.register(ADMIN, asset.id(), unique("SN"), null, "CELSIUS", null, null);
    Actor telemetry = Actor.system("telemetry-service");

    var context = contexts.get(telemetry, withProfile.id());
    assertThat(context.assetId()).isEqualTo(asset.id());
    assertThat(context.assetCriticality()).isEqualTo(Criticality.HIGH);
    assertThat(context.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(context.profile().minTemperature()).isEqualByComparingTo("2.00");
    assertThat(context.profile().maxTemperature()).isEqualByComparingTo("8.00");
    assertThat(context.profile().persistenceWindow()).isEqualTo(Duration.ofMinutes(5));
    assertThat(context.profile().expectedInterval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(context.profile().calibrationValidity()).isNull();
    assertThat(context.profile().version()).isEqualTo(1);

    var batch =
        contexts.getMany(
            telemetry, List.of(withProfile.id(), bare.id(), UUID.randomUUID(), bare.id()));
    assertThat(batch).hasSize(2);
    assertThat(
            batch.stream()
                .filter(c -> c.sensorId().equals(bare.id()))
                .findFirst()
                .orElseThrow()
                .profile())
        .isNull();

    catalog.updateAsset(ADMIN, asset.id(), 1, null, null, Criticality.CRITICAL);
    lifecycle.changeStatus(ADMIN, bare.id(), SensorStatus.INACTIVE, "off");
    assertThat(contexts.get(telemetry, withProfile.id()).assetCriticality())
        .isEqualTo(Criticality.CRITICAL);
    assertThat(contexts.get(telemetry, bare.id()).status()).isEqualTo(SensorStatus.INACTIVE);
    assertThat(contexts.getMany(telemetry, List.of())).isEmpty();
  }

  @Test
  void twoSimultaneousChangesToTheSameSensorLeaveExactlyOneApplied() throws Exception {
    Sensor sensor =
        sensors.register(ADMIN, anAsset().id(), unique("SN"), null, "CELSIUS", null, null);
    long before =
        jdbc.sql(
                "SELECT count(*) FROM sensor_lifecycle_audit WHERE sensor_id = ? AND action = 'STATUS_CHANGED'")
            .param(sensor.id())
            .query(Long.class)
            .single();
    var start = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "concurrent");
                    return true;
                  } catch (StaleVersionException
                      | com.coldguard.asset.domain.BusinessRuleViolationException rejected) {
                    return false;
                  }
                }));
      }
      start.countDown();
      long applied = 0;
      for (var result : results) {
        applied += result.get(30, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0;
      }
      assertThat(applied).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(sensors.get(ADMIN, sensor.id()).status()).isEqualTo(SensorStatus.INACTIVE);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM sensor_lifecycle_audit WHERE sensor_id = ? AND action = 'STATUS_CHANGED'")
                .param(sensor.id())
                .query(Long.class)
                .single())
        .isEqualTo(before + 1);
  }

  // ---- calibration expiry ----------------------------------------------------------------

  /** A sensor whose only calibration expired {@code ago} before now. */
  private Sensor expired(Asset asset, Instant performedAt) {
    return sensors.register(
        ADMIN,
        asset.id(),
        unique("SN"),
        null,
        "CELSIUS",
        new InitialCalibration(CalibrationKind.CALIBRATION, performedAt, "Initial"),
        draft("CELSIUS", Duration.ofHours(1)));
  }

  @Test
  void theDueQueryWalksByKeysetEvenWhenSeveralSensorsExpireAtTheSameInstant() {
    Asset asset = anAsset();
    Instant sameExpiry =
        Instant.now().minus(Duration.ofHours(5)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    List<UUID> ours = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ours.add(expired(asset, sameExpiry).id());
    }
    Sensor notDue =
        sensors.register(
            ADMIN,
            asset.id(),
            unique("SN"),
            null,
            "CELSIUS",
            new InitialCalibration(
                CalibrationKind.CALIBRATION, Instant.now().minusSeconds(10), "Fresh"),
            draft("CELSIUS", Duration.ofDays(1)));
    Sensor noCalibration =
        sensors.register(ADMIN, asset.id(), unique("SN"), null, "CELSIUS", null, null);

    List<UUID> walked = new java.util.ArrayList<>();
    DueSensor after = null;
    Instant now = Instant.now();
    while (true) {
      List<DueSensor> page = sensorRepository.findDueForCalibrationExpiry(now, after, 2);
      page.forEach(d -> walked.add(d.sensorId()));
      if (page.size() < 2) {
        break;
      }
      after = page.get(page.size() - 1);
    }

    assertThat(walked).containsAll(ours).doesNotContain(notDue.id(), noCalibration.id());
    assertThat(walked).doesNotHaveDuplicates();
    // With one shared expiry the keyset order is the database's id order (unsigned bytes, which is
    // not java.util.UUID's signed comparison).
    List<UUID> expected =
        jdbc.sql("SELECT id FROM sensor WHERE id = ANY (?) ORDER BY id")
            .param(ours.toArray(UUID[]::new))
            .query(UUID.class)
            .list();
    assertThat(walked.stream().filter(ours::contains).toList()).containsExactlyElementsOf(expected);
  }

  @Test
  void theJobMovesEligibleSensorsAsTheSystemAndIsolatesAFailingOne() {
    Asset asset = anAsset();
    Instant longAgo = Instant.now().minus(Duration.ofHours(3));
    Sensor active = expired(asset, longAgo);
    Sensor inactive = expired(asset, longAgo.plusSeconds(1));
    lifecycle.changeStatus(ADMIN, inactive.id(), SensorStatus.INACTIVE, "off");
    Sensor valid =
        sensors.register(
            ADMIN,
            asset.id(),
            unique("SN"),
            null,
            "CELSIUS",
            new InitialCalibration(
                CalibrationKind.CALIBRATION, Instant.now().minusSeconds(10), "Fresh"),
            draft("CELSIUS", Duration.ofDays(1)));
    Sensor alreadyInMaintenance = expired(asset, longAgo.plusSeconds(2));
    lifecycle.changeStatus(
        ADMIN, alreadyInMaintenance.id(), SensorStatus.IN_MAINTENANCE, "inspect");
    UUID inconsistent = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO sensor
              (id, serial_number, measurement_unit, asset_id, status, status_changed_at,
               last_calibration_recorded_at, last_calibration_valid_until, created_at, updated_at,
               version)
            VALUES (?, ?, 'CELSIUS', ?, 'ACTIVE', now(), now(), now() - interval '1 hour', now(), now(), 1)
            """)
        .params(inconsistent, unique("SN"), asset.id())
        .update();
    long inconsistentEvents = outboxFor(inconsistent.toString());
    long inconsistentAudit =
        jdbc.sql("SELECT count(*) FROM sensor_lifecycle_audit WHERE sensor_id = ?")
            .param(inconsistent)
            .query(Long.class)
            .single();

    var result = expiry.run();

    assertThat(result.transitioned()).isGreaterThanOrEqualTo(2);
    assertThat(result.failed())
        .as("the sensor without a calibration record failed")
        .isGreaterThanOrEqualTo(1);
    assertThat(sensors.get(ADMIN, active.id()).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(sensors.get(ADMIN, inactive.id()).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(sensors.get(ADMIN, valid.id()).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(sensors.get(ADMIN, alreadyInMaintenance.id()).version()).isEqualTo(2);
    assertThat(
            jdbc.sql("SELECT status FROM sensor WHERE id = ?")
                .param(inconsistent)
                .query(String.class)
                .single())
        .as("a failing sensor is rolled back on its own")
        .isEqualTo("ACTIVE");
    assertThat(outboxFor(inconsistent.toString())).isEqualTo(inconsistentEvents);
    assertThat(
            jdbc.sql("SELECT count(*) FROM sensor_lifecycle_audit WHERE sensor_id = ?")
                .param(inconsistent)
                .query(Long.class)
                .single())
        .isEqualTo(inconsistentAudit);

    var audit =
        jdbc.sql(
                "SELECT action, actor_type, actor_id, reason, previous_value->>'status' AS previous, new_value->>'status' AS next"
                    + " FROM sensor_lifecycle_audit WHERE sensor_id = ? AND actor_type = 'SYSTEM'")
            .param(active.id())
            .query()
            .singleRow();
    assertThat(audit)
        .containsEntry("action", "STATUS_CHANGED")
        .containsEntry("actor_id", "calibration-expiry-job")
        .containsEntry("reason", "calibración/verificación vencida")
        .containsEntry("previous", "ACTIVE")
        .containsEntry("next", "IN_MAINTENANCE");
    assertThat(
            jdbc.sql(
                    "SELECT event_type || ':' || aggregate_version FROM outbox_event WHERE aggregate_id = ? ORDER BY aggregate_version")
                .param(active.id().toString())
                .query(String.class)
                .list())
        .containsExactly(
            "SensorCalibrationRecorded:1",
            "OperationalProfileUpdated:2",
            "SensorCalibrationExpired:3",
            "SensorStatusChanged:4");
    assertThat(
            jdbc.sql(
                    "SELECT (payload->'actor'->>'type') || '/' || (payload->'actor'->>'id') FROM outbox_event WHERE aggregate_id = ? AND event_type = 'SensorCalibrationExpired'")
                .param(active.id().toString())
                .query(String.class)
                .single())
        .isEqualTo("SYSTEM/calibration-expiry-job");

    long events = outboxFor(active.id().toString());
    expiry.run();
    assertThat(outboxFor(active.id().toString()))
        .as("a second run changes nothing")
        .isEqualTo(events);
    assertThat(sensors.get(ADMIN, active.id()).version()).isEqualTo(2);
  }
}
