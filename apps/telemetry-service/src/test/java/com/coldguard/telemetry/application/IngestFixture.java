package com.coldguard.telemetry.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.Criticality;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.InMemoryTelemetryStore;
import com.coldguard.telemetry.support.MutableClock;
import com.coldguard.telemetry.support.NoOpTransactionManager;
import com.coldguard.telemetry.support.RecordingEvents;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The ingestion use case wired over in-memory ports, with a controllable clock. */
class IngestFixture {

  static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
  static final Actor SIMULATOR = Actor.system("sensor-simulator");
  static final Actor ADMIN = new Actor("admin-1", Set.of(Role.PLATFORM_ADMIN));

  static final EvaluationProfile PROFILE =
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

  /** Counts every reading recorded, to check the metric is fed for each result. */
  final List<String> metrics = new ArrayList<>();

  final MutableClock clock = new MutableClock(NOW);
  final InMemoryTelemetryStore store = new InMemoryTelemetryStore();
  final RecordingEvents events = new RecordingEvents();

  final IngestReadingsService service =
      new IngestReadingsService(
          store.contexts,
          store.readingRepository,
          store.conditionRepository,
          events,
          (source, outcome, eligible, breached) ->
              metrics.add(source + "/" + outcome + "/" + eligible + "/" + breached),
          new IngestSettings(5, Duration.ofSeconds(30)),
          clock,
          new NoOpTransactionManager());

  /** Registers a sensor in the fake Asset. */
  SensorContext sensor(SensorStatus status, EvaluationProfile profile) {
    SensorContext context =
        new SensorContext(UUID.randomUUID(), UUID.randomUUID(), Criticality.HIGH, status, profile);
    store.assetContexts.put(context.sensorId(), context);
    return context;
  }

  SensorContext activeSensor() {
    return sensor(SensorStatus.ACTIVE, PROFILE);
  }

  static IncomingReading reading(SensorContext sensor, double value, long secondsBeforeNow) {
    return new IncomingReading(
        UUID.randomUUID().toString(),
        sensor.sensorId().toString(),
        NOW.minusSeconds(secondsBeforeNow),
        value,
        "CELSIUS");
  }
}
