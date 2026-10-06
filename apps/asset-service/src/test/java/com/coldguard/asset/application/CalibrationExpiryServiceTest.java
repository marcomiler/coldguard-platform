package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.application.SensorService.InitialCalibration;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.support.EventContract;
import com.coldguard.asset.support.NoOpTransactionManager;
import com.coldguard.commons.messaging.EventActor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalibrationExpiryServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final SensorService sensors = f.sensors(Duration.ofDays(90));
  private final SensorLifecycleService lifecycle = f.lifecycle(Duration.ofDays(90));
  private final UUID assetId = f.anAsset().id();
  private int serial;

  private CalibrationExpiryService service(int batchSize) {
    return service(f.store.sensorRepository, batchSize);
  }

  private CalibrationExpiryService service(SensorRepository repository, int batchSize) {
    return new CalibrationExpiryService(
        repository,
        f.store.calibrationRepository,
        f.store.historyRepository,
        f.events,
        f.clock,
        new NoOpTransactionManager(),
        batchSize);
  }

  /**
   * An ACTIVE sensor whose only calibration expires {@code expiresIn} from now (negative: already).
   */
  private Sensor sensorExpiring(Duration expiresIn) {
    boolean inTheFuture = expiresIn.compareTo(Duration.ZERO) > 0;
    Duration validity = inTheFuture ? expiresIn.plusSeconds(60) : Duration.ofHours(1);
    Instant performedAt = inTheFuture ? NOW.minusSeconds(60) : NOW.plus(expiresIn).minus(validity);
    return sensors.register(
        ADMIN,
        assetId,
        "SN-" + ++serial,
        null,
        "CELSIUS",
        new InitialCalibration(CalibrationKind.CALIBRATION, performedAt, "Initial"),
        ServiceFixture.draft(UUID.randomUUID(), "CELSIUS", validity));
  }

  private Sensor stored(Sensor sensor) {
    return f.store.sensors.get(sensor.id());
  }

  @Test
  void anExpiredActiveSensorMovesToMaintenanceAsTheSystemWithAFixedReason() {
    Sensor sensor = sensorExpiring(Duration.ofMinutes(-30));
    f.events.published.clear();
    f.store.history.clear();
    f.clock.advance(Duration.ofMinutes(1));
    Instant detectedAt = f.clock.instant();

    var result = service(10).run();

    assertThat(result).isEqualTo(new CalibrationExpiryService.Result(1, 1, 0));
    Sensor moved = stored(sensor);
    assertThat(moved.status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(moved.statusChangedAt()).isEqualTo(detectedAt);
    assertThat(moved.version()).isEqualTo(2);
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("STATUS_CHANGED");
              assertThat(h.actorType()).isEqualTo("SYSTEM");
              assertThat(h.actorId()).isEqualTo("calibration-expiry-job");
              assertThat(h.reason()).isEqualTo("calibración/verificación vencida");
              assertThat(h.previousValue()).containsEntry("status", "ACTIVE");
              assertThat(h.newValue()).containsEntry("status", "IN_MAINTENANCE");
              assertThat(h.occurredAt()).isEqualTo(detectedAt);
            });
    assertThat(f.events.published.stream().map(e -> e.eventType()))
        .containsExactly("SensorCalibrationExpired", "SensorStatusChanged");
    f.events.published.forEach(
        e -> {
          assertThat(e.actor()).isEqualTo(EventActor.system("calibration-expiry-job"));
          EventContract.assertConforms(e);
        });
    var expired =
        tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .valueToTree(f.events.published.get(0).payload());
    assertThat(expired.get("calibrationId").asString())
        .isEqualTo(f.store.calibrations.get(0).id().toString());
    assertThat(Instant.parse(expired.get("expiredAt").asString()))
        .isEqualTo(f.store.calibrations.get(0).validUntil());
    assertThat(Instant.parse(expired.get("detectedAt").asString())).isEqualTo(detectedAt);
  }

  @Test
  void anExpiredInactiveSensorAlsoMovesToMaintenance() {
    Sensor sensor = sensorExpiring(Duration.ofDays(5));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "off");
    f.clock.advance(Duration.ofDays(6));

    var result = service(10).run();

    assertThat(result.transitioned()).isEqualTo(1);
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(f.store.history.get(f.store.history.size() - 1).previousValue())
        .containsEntry("status", "INACTIVE");
  }

  @Test
  void onlyExpiredActiveOrInactiveSensorsWithACalibrationAreTouched() {
    Sensor stillValid = sensorExpiring(Duration.ofMinutes(30));
    Sensor justExpiring = sensorExpiring(Duration.ZERO);
    Sensor inMaintenance = sensorExpiring(Duration.ofMinutes(-30));
    f.clock.advance(Duration.ofSeconds(1));
    lifecycle.changeStatus(ADMIN, inMaintenance.id(), SensorStatus.IN_MAINTENANCE, "inspect");
    Sensor retired = sensorExpiring(Duration.ofMinutes(-30));
    lifecycle.retire(ADMIN, retired.id(), "end");
    Sensor neverCalibrated =
        sensors.register(ADMIN, assetId, "SN-BARE", null, "CELSIUS", null, null);
    f.events.published.clear();
    f.clock.advance(Duration.ofSeconds(-1));
    Instant versionBefore = stored(justExpiring).updatedAt();

    var result = service(10).run();

    assertThat(result.transitioned())
        .as("a calibration expiring exactly now has not expired")
        .isZero();
    assertThat(stored(stillValid).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(stored(justExpiring).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(stored(justExpiring).updatedAt()).isEqualTo(versionBefore);
    assertThat(stored(inMaintenance).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(stored(retired).status()).isEqualTo(SensorStatus.RETIRED);
    assertThat(stored(neverCalibrated).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void runningTwiceDoesNothingTheSecondTime() {
    sensorExpiring(Duration.ofMinutes(-30));
    sensorExpiring(Duration.ofMinutes(-10));
    CalibrationExpiryService job = service(10);

    assertThat(job.run().transitioned()).isEqualTo(2);
    f.events.published.clear();
    int history = f.store.history.size();

    assertThat(job.run()).isEqualTo(new CalibrationExpiryService.Result(0, 0, 0));
    assertThat(f.events.published).isEmpty();
    assertThat(f.store.history).hasSize(history);
  }

  @Test
  void aFailingSensorDoesNotStopTheOthersAndIsRetriedNextRun() {
    Sensor first = sensorExpiring(Duration.ofMinutes(-50));
    Sensor poisoned = sensorExpiring(Duration.ofMinutes(-40));
    Sensor third = sensorExpiring(Duration.ofMinutes(-30));
    f.events.published.clear();
    Set<UUID> failing = new HashSet<>(Set.of(poisoned.id()));

    var result = service(failingUpdates(failing), 10).run();

    assertThat(result).isEqualTo(new CalibrationExpiryService.Result(3, 2, 1));
    assertThat(stored(first).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(stored(third).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(stored(poisoned).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(f.events.published).hasSize(4);

    failing.clear();
    assertThat(service(10).run().transitioned()).isEqualTo(1);
    assertThat(stored(poisoned).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
  }

  @Test
  void aSmallBatchWalksEverythingAndTerminatesEvenIfOneSensorAlwaysFails() {
    List<Sensor> all = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      all.add(sensorExpiring(Duration.ofMinutes(-60 + i)));
    }
    Sensor poisoned = all.get(2);

    var result = service(failingUpdates(Set.of(poisoned.id())), 2).run();

    assertThat(result).isEqualTo(new CalibrationExpiryService.Result(5, 4, 1));
    all.stream()
        .filter(s -> !s.id().equals(poisoned.id()))
        .forEach(s -> assertThat(stored(s).status()).isEqualTo(SensorStatus.IN_MAINTENANCE));
    assertThat(stored(poisoned).status()).isEqualTo(SensorStatus.ACTIVE);
  }

  @Test
  void aSensorThatChangedAfterBeingSelectedIsSkipped() {
    Sensor sensor = sensorExpiring(Duration.ofMinutes(-30));
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "off by hand");
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "inspection");
    f.events.published.clear();
    SensorRepository staleSelection =
        proxy(
            (method, args) ->
                method.getName().equals("findDueForCalibrationExpiry")
                    ? (Object)
                        (args[1] == null
                            ? List.of(new DueSensor(sensor.id(), NOW.minusSeconds(1)))
                            : List.of())
                    : invokeOn(f.store.sensorRepository, method, args));

    var result = service(staleSelection, 10).run();

    assertThat(result).isEqualTo(new CalibrationExpiryService.Result(1, 0, 0));
    assertThat(f.events.published).isEmpty();
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
  }

  @Test
  void aCalibrationWithoutItsRecordIsAFailureNotASilentSkip() {
    Sensor sensor = sensorExpiring(Duration.ofMinutes(-30));
    f.store.calibrations.clear();

    var result = service(10).run();

    assertThat(result).isEqualTo(new CalibrationExpiryService.Result(1, 0, 1));
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.ACTIVE);
  }

  @Test
  void theBatchSizeMustBePositive() {
    assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
  }

  // ---- helpers ------------------------------------------------------------------------------

  private interface Handler {
    Object handle(java.lang.reflect.Method method, Object[] args) throws Throwable;
  }

  private static Object invokeOn(Object target, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static SensorRepository proxy(Handler handler) {
    return (SensorRepository)
        Proxy.newProxyInstance(
            SensorRepository.class.getClassLoader(),
            new Class<?>[] {SensorRepository.class},
            (p, method, args) -> handler.handle(method, args));
  }

  /** The real repository, except that updating one of {@code failing} throws. */
  private SensorRepository failingUpdates(Set<UUID> failing) {
    return proxy(
        (method, args) -> {
          if (method.getName().equals("update") && failing.contains(((Sensor) args[0]).id())) {
            throw new IllegalStateException("simulated failure");
          }
          return invokeOn(f.store.sensorRepository, method, args);
        });
  }
}
