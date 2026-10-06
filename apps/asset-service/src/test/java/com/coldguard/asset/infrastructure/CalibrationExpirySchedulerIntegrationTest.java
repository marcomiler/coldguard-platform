package com.coldguard.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.asset.application.AssetCatalogService;
import com.coldguard.asset.application.OperationalProfileDraft;
import com.coldguard.asset.application.SensorService;
import com.coldguard.asset.application.SensorService.InitialCalibration;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * The expiry job wired the way it runs in the service: triggered by its own cron (every second
 * here) with nobody calling it, so the configuration, the schedule, the system actor and the metric
 * are all exercised together.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "coldguard.outbox.enabled=false",
      "coldguard.asset.calibration-expiry.enabled=true",
      "coldguard.asset.calibration-expiry.cron=* * * * * *"
    })
class CalibrationExpirySchedulerIntegrationTest {

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
  @Autowired MeterRegistry meters;
  @Autowired JdbcClient jdbc;

  @Test
  void aSensorWithAShortCalibrationMovesToMaintenanceByItself() throws Exception {
    var org = catalog.createOrganization(ADMIN, "Org " + UUID.randomUUID());
    var site = catalog.createSite(ADMIN, org.id(), "Main", null);
    Asset asset =
        catalog.registerAsset(
            ADMIN, site.id(), "Room " + UUID.randomUUID(), null, Criticality.HIGH);
    OperationalProfileDraft shortValidity =
        new OperationalProfileDraft(
            UUID.randomUUID(),
            new BigDecimal("2"),
            new BigDecimal("8"),
            "CELSIUS",
            new BigDecimal("1"),
            new BigDecimal("3"),
            new BigDecimal("6"),
            3,
            Duration.ofMinutes(5),
            Duration.ofSeconds(5),
            Duration.ofSeconds(2));
    Sensor expiring =
        sensors.register(
            ADMIN,
            asset.id(),
            "SN-" + UUID.randomUUID(),
            null,
            "CELSIUS",
            new InitialCalibration(
                CalibrationKind.CALIBRATION, Instant.now().minusSeconds(1), "Short demo"),
            shortValidity);
    Sensor healthy =
        sensors.register(
            ADMIN,
            asset.id(),
            "SN-" + UUID.randomUUID(),
            null,
            "CELSIUS",
            new InitialCalibration(
                CalibrationKind.CALIBRATION, Instant.now().minusSeconds(1), "Long"),
            new OperationalProfileDraft(
                UUID.randomUUID(),
                new BigDecimal("2"),
                new BigDecimal("8"),
                "CELSIUS",
                new BigDecimal("1"),
                new BigDecimal("3"),
                new BigDecimal("6"),
                3,
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
                Duration.ofDays(30)));
    assertThat(sensors.get(ADMIN, expiring.id()).status()).isEqualTo(SensorStatus.ACTIVE);

    long deadline = System.currentTimeMillis() + 30_000;
    while (sensors.get(ADMIN, expiring.id()).status() != SensorStatus.IN_MAINTENANCE
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(250);
    }

    assertThat(sensors.get(ADMIN, expiring.id()).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(sensors.get(ADMIN, healthy.id()).status()).isEqualTo(SensorStatus.ACTIVE);
    var audit =
        jdbc.sql(
                "SELECT actor_type, actor_id, reason FROM sensor_lifecycle_audit WHERE sensor_id = ? AND action = 'STATUS_CHANGED'")
            .param(expiring.id())
            .query()
            .singleRow();
    assertThat(audit)
        .containsEntry("actor_type", "SYSTEM")
        .containsEntry("actor_id", "calibration-expiry-job")
        .containsEntry("reason", "calibración/verificación vencida");
    assertThat(
            jdbc.sql(
                    "SELECT event_type FROM outbox_event WHERE aggregate_id = ? AND aggregate_version > 2 ORDER BY aggregate_version")
                .param(expiring.id().toString())
                .query(String.class)
                .list())
        .containsExactly("SensorCalibrationExpired", "SensorStatusChanged");
    assertThat(meters.get("coldguard.asset.calibration.expired").counter().count())
        .isGreaterThanOrEqualTo(1.0);

    long events =
        jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
            .param(expiring.id().toString())
            .query(Long.class)
            .single();
    Thread.sleep(3_000);
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
                .param(expiring.id().toString())
                .query(Long.class)
                .single())
        .as("later runs leave the sensor alone")
        .isEqualTo(events);
  }
}
