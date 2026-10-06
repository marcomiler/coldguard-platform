package com.coldguard.telemetry.application;

import static com.coldguard.telemetry.application.IngestFixture.ADMIN;
import static com.coldguard.telemetry.application.IngestFixture.NOW;
import static com.coldguard.telemetry.application.IngestFixture.SIMULATOR;
import static com.coldguard.telemetry.application.IngestFixture.reading;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.AnomalyType;
import com.coldguard.telemetry.domain.IneligibilityReason;
import com.coldguard.telemetry.domain.MagnitudeLevel;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.EventContract;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestReadingsServiceTest {

  private final IngestFixture f = new IngestFixture();

  private List<ReadingResult> simulate(IncomingReading... readings) {
    return f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, List.of(readings));
  }

  private static tools.jackson.databind.JsonNode payload(OutboundEvent event) {
    return tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(event.payload());
  }

  // ---- the normal path --------------------------------------------------------------------

  @Test
  void readingsWithinTheRangeAreAcceptedAndRaiseNothing() {
    SensorContext sensor = f.activeSensor();

    var results =
        simulate(reading(sensor, 5.0, 10), reading(sensor, 2.0, 5), reading(sensor, 8.0, 1));

    assertThat(results)
        .allSatisfy(
            r -> {
              assertThat(r.outcome()).isEqualTo(ReadingOutcome.ACCEPTED);
              assertThat(r.eligible()).isTrue();
              assertThat(r.breached()).isFalse();
              assertThat(r.rejectionCode()).isNull();
            });
    assertThat(f.store.readings).hasSize(3);
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void aReadingOutsideTheRangeIsStoredAsEvidenceAndAnnounced() {
    SensorContext sensor = f.activeSensor();
    IncomingReading hot = reading(sensor, 12.0, 3);

    var results = simulate(hot);

    assertThat(results.get(0).breached()).isTrue();
    var stored = f.store.readings.get(UUID.fromString(hot.readingId()));
    assertThat(stored.breached()).isTrue();
    assertThat(stored.anomalyType()).isEqualTo(AnomalyType.TEMPERATURE_ABOVE_MAX);
    assertThat(stored.magnitude()).isEqualTo(MagnitudeLevel.HIGH);
    assertThat(stored.source()).isEqualTo(ReadingSource.SIMULATOR);
    assertThat(stored.assetId()).isEqualTo(sensor.assetId());
    assertThat(stored.receivedAt()).isEqualTo(NOW);

    OutboundEvent event = f.events.only();
    assertThat(event.eventType()).isEqualTo("TelemetryThresholdBreached");
    assertThat(event.routingKey()).isEqualTo("telemetry.threshold-breached");
    assertThat(event.aggregateType()).isEqualTo("Sensor");
    assertThat(event.aggregateId()).isEqualTo(sensor.sensorId().toString());
    assertThat(event.actor()).isEqualTo(EventActor.system("sensor-simulator"));
    assertThat(event.occurredAt()).isEqualTo(NOW.minusSeconds(3));
    var body = payload(event);
    assertThat(body.get("readingId").asString()).isEqualTo(hot.readingId());
    assertThat(body.get("assetCriticality").asString()).isEqualTo("HIGH");
    assertThat(body.get("anomalyType").asString()).isEqualTo("TEMPERATURE_ABOVE_MAX");
    assertThat(body.get("deviation").decimalValue()).isEqualByComparingTo("4.0");
    assertThat(body.get("thresholdMin").decimalValue()).isEqualByComparingTo("2.0");
    assertThat(body.get("thresholdMax").decimalValue()).isEqualByComparingTo("8.0");
    assertThat(body.get("magnitude").asString()).isEqualTo("HIGH");
    assertThat(body.get("persistent").asBoolean()).isFalse();
    EventContract.assertConforms(event);
  }

  @Test
  void theConditionBecomesPersistentAfterTheConfiguredConsecutiveReadingsAndRecoveryResetsIt() {
    SensorContext sensor = f.activeSensor();

    simulate(reading(sensor, 9.0, 30));
    simulate(reading(sensor, 9.5, 20));
    simulate(reading(sensor, 10.0, 10));
    simulate(reading(sensor, 5.0, 5));
    simulate(reading(sensor, 10.0, 2));

    assertThat(f.events.published.stream().map(e -> payload(e).get("persistent").asBoolean()))
        .containsExactly(false, false, true, false);
    assertThat(f.store.conditions.get(sensor.sensorId()).breachStreak()).isEqualTo(1);
  }

  @Test
  void aStreakInsideOneBatchIsEvaluatedInOrderOfRecordedAtNotOfArrival() {
    SensorContext sensor = f.activeSensor();

    simulate(reading(sensor, 9.0, 10), reading(sensor, 9.0, 30), reading(sensor, 9.0, 20));

    assertThat(f.events.published.stream().map(e -> payload(e).get("persistent").asBoolean()))
        .containsExactly(false, false, true);
    assertThat(f.events.published.stream().map(e -> payload(e).get("recordedAt").asString()))
        .isSorted();
  }

  @Test
  void aLateReadingIsKeptAndFlaggedButStaysOutOfTheStreakAndRaisesNoEvent() {
    SensorContext sensor = f.activeSensor();
    simulate(reading(sensor, 9.0, 5));
    f.events.published.clear();
    IncomingReading late = reading(sensor, 12.0, 60);

    var results = simulate(late);

    assertThat(results.get(0).outcome()).isEqualTo(ReadingOutcome.ACCEPTED);
    assertThat(results.get(0).breached()).isTrue();
    assertThat(f.store.readings.get(UUID.fromString(late.readingId())).breached()).isTrue();
    assertThat(f.events.published).isEmpty();
    assertThat(f.store.conditions.get(sensor.sensorId()).breachStreak()).isEqualTo(1);
  }

  @Test
  void theSameReadingProducesTheSameEvaluationWhateverItsSource() {
    SensorContext forSimulator = f.activeSensor();
    SensorContext forAdmin = f.activeSensor();
    IncomingReading fromSimulator = reading(forSimulator, 12.0, 3);
    IncomingReading fromAdmin = reading(forAdmin, 12.0, 3);

    var simulated = simulate(fromSimulator);
    var injected = f.service.ingest(ADMIN, ReadingSource.TEST_INJECTION, List.of(fromAdmin));

    assertThat(injected.get(0).breached()).isEqualTo(simulated.get(0).breached());
    assertThat(injected.get(0).eligible()).isEqualTo(simulated.get(0).eligible());
    var one = f.store.readings.get(UUID.fromString(fromSimulator.readingId()));
    var other = f.store.readings.get(UUID.fromString(fromAdmin.readingId()));
    assertThat(other.anomalyType()).isEqualTo(one.anomalyType());
    assertThat(other.magnitude()).isEqualTo(one.magnitude());
    assertThat(one.source()).isEqualTo(ReadingSource.SIMULATOR);
    assertThat(other.source()).isEqualTo(ReadingSource.TEST_INJECTION);
    assertThat(f.events.published).hasSize(2);
    var first = payload(f.events.published.get(0));
    var second = payload(f.events.published.get(1));
    assertThat(second.get("deviation").decimalValue())
        .isEqualByComparingTo(first.get("deviation").decimalValue());
    assertThat(second.get("magnitude")).isEqualTo(first.get("magnitude"));
    assertThat(second.get("persistent")).isEqualTo(first.get("persistent"));
    assertThat(f.events.published.get(0).actor()).isEqualTo(EventActor.system("sensor-simulator"));
    assertThat(f.events.published.get(1).actor()).isEqualTo(EventActor.user("admin-1"));
  }

  @Test
  void twoReadingsOfOneSensorAtTheSameInstantCountOnlyTheFirstForTheStreak() {
    SensorContext sensor = f.activeSensor();

    simulate(reading(sensor, 12.0, 3));
    simulate(reading(sensor, 12.0, 3));

    assertThat(f.events.published).hasSize(1);
    assertThat(f.store.readings).hasSize(2);
  }

  // ---- idempotency ------------------------------------------------------------------------

  @Test
  void resendingABatchReportsDuplicatesAndRaisesNothingNew() {
    SensorContext sensor = f.activeSensor();
    IncomingReading hot = reading(sensor, 12.0, 3);
    IncomingReading fine = reading(sensor, 5.0, 2);
    simulate(hot, fine);
    int events = f.events.published.size();
    int stored = f.store.readings.size();

    var again = simulate(hot, fine);

    assertThat(again).extracting(ReadingResult::outcome).containsOnly(ReadingOutcome.DUPLICATE);
    assertThat(again)
        .extracting(ReadingResult::readingId)
        .containsExactly(hot.readingId(), fine.readingId());
    assertThat(f.events.published).hasSize(events);
    assertThat(f.store.readings).hasSize(stored);
  }

  @Test
  void aReadingRepeatedInsideTheSameBatchCountsOnce() {
    SensorContext sensor = f.activeSensor();
    IncomingReading hot = reading(sensor, 12.0, 3);

    var results = simulate(hot, hot);

    assertThat(results)
        .extracting(ReadingResult::outcome)
        .containsExactly(ReadingOutcome.ACCEPTED, ReadingOutcome.DUPLICATE);
    assertThat(f.events.published).hasSize(1);
    assertThat(f.store.readings).hasSize(1);
  }

  // ---- eligibility ------------------------------------------------------------------------

  @Test
  void readingsOfASensorThatIsNotActiveAreKeptAsEvidenceButNeverEvaluated() {
    for (SensorStatus status :
        new SensorStatus[] {
          SensorStatus.IN_MAINTENANCE, SensorStatus.INACTIVE, SensorStatus.RETIRED
        }) {
      SensorContext sensor = f.sensor(status, IngestFixture.PROFILE);
      IncomingReading hot = reading(sensor, 40.0, 3);

      var result = simulate(hot).get(0);

      assertThat(result.outcome()).as(status.name()).isEqualTo(ReadingOutcome.ACCEPTED);
      assertThat(result.eligible()).isFalse();
      assertThat(result.breached()).isFalse();
      var stored = f.store.readings.get(UUID.fromString(hot.readingId()));
      assertThat(stored.eligible()).isFalse();
      assertThat(stored.ineligibilityReason()).isEqualTo(IneligibilityReason.SENSOR_NOT_ACTIVE);
      assertThat(stored.breached()).isFalse();
    }
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void aSensorWithoutAProfileIsKeptButNotEvaluated() {
    SensorContext sensor = f.sensor(SensorStatus.ACTIVE, null);
    IncomingReading hot = reading(sensor, 40.0, 3);

    var result = simulate(hot).get(0);

    assertThat(result.eligible()).isFalse();
    assertThat(f.store.readings.get(UUID.fromString(hot.readingId())).ineligibilityReason())
        .isEqualTo(IneligibilityReason.NO_PROFILE);
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void anIneligibleReadingDoesNotTouchTheStreakButCountsAsALifeSign() {
    SensorContext sensor = f.activeSensor();
    simulate(reading(sensor, 9.0, 30));
    f.store.assetContexts.put(
        sensor.sensorId(),
        new SensorContext(
            sensor.sensorId(),
            sensor.assetId(),
            sensor.assetCriticality(),
            SensorStatus.IN_MAINTENANCE,
            sensor.profile()));
    f.clock.advance(Duration.ofSeconds(20));

    simulate(reading(sensor, 40.0, 10));

    SensorCondition condition = f.store.conditions.get(sensor.sensorId());
    assertThat(condition.breachStreak()).isEqualTo(1);
    assertThat(condition.sensorStatus()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(condition.lastReadingAt()).isEqualTo(NOW.plusSeconds(20));
  }

  // ---- rejections -------------------------------------------------------------------------

  @Test
  void anInvalidReadingIsRejectedWithItsCodeWithoutAbortingTheOthers() {
    SensorContext sensor = f.activeSensor();
    String sensorId = sensor.sensorId().toString();
    String id = UUID.randomUUID().toString();
    var batch =
        List.of(
            new IncomingReading("not-a-uuid", sensorId, NOW, 5, "CELSIUS"),
            new IncomingReading(id, "not-a-uuid", NOW, 5, "CELSIUS"),
            new IncomingReading(UUID.randomUUID().toString(), sensorId, null, 5, "CELSIUS"),
            new IncomingReading(
                UUID.randomUUID().toString(), sensorId, NOW.plusSeconds(31), 5, "CELSIUS"),
            new IncomingReading(UUID.randomUUID().toString(), sensorId, NOW, 5, " "));
    var more =
        List.of(
            new IncomingReading(UUID.randomUUID().toString(), sensorId, NOW, Double.NaN, "CELSIUS"),
            new IncomingReading(
                UUID.randomUUID().toString(), sensorId, NOW, Double.POSITIVE_INFINITY, "CELSIUS"),
            new IncomingReading(UUID.randomUUID().toString(), sensorId, NOW, 100000, "CELSIUS"),
            new IncomingReading(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), NOW, 5, "CELSIUS"),
            new IncomingReading(UUID.randomUUID().toString(), sensorId, NOW, 5, "FAHRENHEIT"));
    IncomingReading good = reading(sensor, 5.0, 1);

    var first = f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);
    var second = f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, more);
    var third = simulate(good);

    assertThat(first)
        .extracting(ReadingResult::rejectionCode)
        .containsExactly(
            "INVALID_READING_ID",
            "INVALID_SENSOR_ID",
            "INVALID_RECORDED_AT",
            "RECORDED_AT_IN_FUTURE",
            "INVALID_UNIT");
    assertThat(second)
        .extracting(ReadingResult::rejectionCode)
        .containsExactly(
            "INVALID_VALUE", "INVALID_VALUE", "INVALID_VALUE", "SENSOR_NOT_FOUND", "UNIT_MISMATCH");
    assertThat(first).extracting(ReadingResult::outcome).containsOnly(ReadingOutcome.REJECTED);
    assertThat(first.get(0).readingId()).isEqualTo("not-a-uuid");
    assertThat(third.get(0).outcome()).isEqualTo(ReadingOutcome.ACCEPTED);
    assertThat(f.store.readings).hasSize(1);
  }

  @Test
  void aRejectedReadingDoesNotAbortTheValidOnesInTheSameBatch() {
    SensorContext sensor = f.activeSensor();
    IncomingReading good = reading(sensor, 5.0, 2);
    IncomingReading wrongUnit =
        new IncomingReading(
            UUID.randomUUID().toString(), sensor.sensorId().toString(), NOW, 5, "KELVIN");
    IncomingReading hot = reading(sensor, 12.0, 1);

    var results = simulate(good, wrongUnit, hot);

    assertThat(results)
        .extracting(ReadingResult::outcome)
        .containsExactly(ReadingOutcome.ACCEPTED, ReadingOutcome.REJECTED, ReadingOutcome.ACCEPTED);
    assertThat(f.store.readings).hasSize(2);
    assertThat(f.events.published).hasSize(1);
  }

  @Test
  void theUnitIsComparedIgnoringCase() {
    SensorContext sensor = f.activeSensor();

    var result =
        simulate(
                new IncomingReading(
                    UUID.randomUUID().toString(), sensor.sensorId().toString(), NOW, 5, "celsius"))
            .get(0);

    assertThat(result.outcome()).isEqualTo(ReadingOutcome.ACCEPTED);
  }

  // ---- request level ----------------------------------------------------------------------

  @Test
  void aBatchAboveTheLimitIsRefusedAsAWhole() {
    SensorContext sensor = f.activeSensor();
    List<IncomingReading> batch = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      batch.add(reading(sensor, 5.0, i));
    }

    assertThatThrownBy(() -> f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch))
        .isInstanceOf(BatchTooLargeException.class);
    assertThat(f.store.readings).isEmpty();
    assertThat(f.store.assetCalls).isEmpty();
  }

  @Test
  void aSourceIsRequired() {
    assertThatThrownBy(() -> f.service.ingest(SIMULATOR, null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyTheSimulatorMayIngestSimulatedReadingsAndOnlyAnAdministratorTestInjections() {
    SensorContext sensor = f.activeSensor();
    List<IncomingReading> batch = List.of(reading(sensor, 5.0, 1));
    Actor supervisor = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));
    Actor otherSystem = Actor.system("telemetry-job");

    assertThat(f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch)).hasSize(1);
    assertThat(
            f.service.ingest(ADMIN, ReadingSource.TEST_INJECTION, List.of(reading(sensor, 5.0, 2))))
        .hasSize(1);

    assertThatThrownBy(() -> f.service.ingest(SIMULATOR, ReadingSource.TEST_INJECTION, batch))
        .isInstanceOf(TelemetryAccessDeniedException.class);
    assertThatThrownBy(() -> f.service.ingest(ADMIN, ReadingSource.SIMULATOR, batch))
        .isInstanceOf(TelemetryAccessDeniedException.class);
    for (Actor denied : new Actor[] {null, supervisor, otherSystem}) {
      for (ReadingSource source : ReadingSource.values()) {
        assertThatThrownBy(() -> f.service.ingest(denied, source, batch))
            .as("%s as %s", source, denied)
            .isInstanceOf(TelemetryAccessDeniedException.class);
      }
    }
    assertThat(f.store.readings).hasSize(2);
  }

  // ---- Asset -----------------------------------------------------------------------------

  @Test
  void anUnavailableAssetFailsTheWholeRequestBeforeAnythingIsStored() {
    SensorContext sensor = f.activeSensor();
    f.store.assetFailure = new AssetUnavailableException("down", null);

    assertThatThrownBy(() -> simulate(reading(sensor, 12.0, 1), reading(sensor, 5.0, 2)))
        .isInstanceOf(AssetUnavailableException.class);

    assertThat(f.store.readings).isEmpty();
    assertThat(f.store.conditions).isEmpty();
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void assetIsAskedOnceForTheDistinctSensorsOfTheBatchAndNotAtAllWhenNothingIsValid() {
    SensorContext a = f.activeSensor();
    SensorContext b = f.activeSensor();

    simulate(reading(a, 5.0, 3), reading(b, 5.0, 2), reading(a, 5.0, 1));
    f.store.assetCalls.clear();
    simulate(new IncomingReading("bad", a.sensorId().toString(), NOW, 5, "CELSIUS"));

    assertThat(f.store.assetCalls).isEmpty();
  }

  @Test
  void assetIsCalledOncePerBatchWithEachSensorOnce() {
    SensorContext a = f.activeSensor();
    SensorContext b = f.activeSensor();

    simulate(reading(a, 5.0, 3), reading(b, 5.0, 2), reading(a, 5.0, 1));

    assertThat(f.store.assetCalls).hasSize(1);
    assertThat(f.store.assetCalls.get(0)).containsExactlyInAnyOrder(a.sensorId(), b.sensorId());
  }

  // ---- several sensors and connectivity --------------------------------------------------

  @Test
  void theSensorsOfABatchAreLockedInAStableOrder() {
    List<SensorContext> sensors = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      sensors.add(f.activeSensor());
    }
    List<IncomingReading> batch = new ArrayList<>();
    for (int i = sensors.size() - 1; i >= 0; i--) {
      batch.add(reading(sensors.get(i), 5.0, i + 1));
    }

    f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);

    assertThat(f.store.lockOrder).isSorted();
    assertThat(f.store.lockOrder).hasSize(5);
  }

  @Test
  void aSensorThatWasSilentIsReportingAgainAndItsMarkIsCleared() {
    SensorContext sensor = f.activeSensor();
    simulate(reading(sensor, 5.0, 100));
    SensorCondition silent = f.store.conditions.get(sensor.sensorId());
    f.store.conditions.put(
        sensor.sensorId(),
        new SensorCondition(
            silent.sensorId(),
            silent.assetId(),
            silent.sensorStatus(),
            silent.expectedIntervalSeconds(),
            silent.lastReadingAt(),
            silent.lastEvaluatedRecordedAt(),
            null,
            0,
            null,
            NOW.minusSeconds(30),
            null,
            silent.version()));
    f.clock.advance(Duration.ofSeconds(60));

    simulate(reading(sensor, 5.0, 1));

    SensorCondition condition = f.store.conditions.get(sensor.sensorId());
    assertThat(condition.connectivityLostAt()).isNull();
    assertThat(condition.lastReadingAt()).isEqualTo(NOW.plusSeconds(60));
  }

  // ---- metrics ----------------------------------------------------------------------------

  @Test
  void everyResultFeedsTheReadingsMetricWithItsOutcome() {
    SensorContext sensor = f.activeSensor();
    IncomingReading hot = reading(sensor, 12.0, 2);

    simulate(hot, reading(sensor, 5.0, 1), new IncomingReading("bad", "x", NOW, 1, "C"));
    simulate(hot);

    assertThat(f.metrics)
        .containsExactly(
            "SIMULATOR/ACCEPTED/true/true",
            "SIMULATOR/ACCEPTED/true/false",
            "SIMULATOR/REJECTED/false/false",
            "SIMULATOR/DUPLICATE/false/false");
  }

  @Test
  void anEmptyBatchIsValidAndDoesNothing() {
    assertThat(f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, List.of())).isEmpty();
    assertThat(f.store.assetCalls).isEmpty();
  }
}
