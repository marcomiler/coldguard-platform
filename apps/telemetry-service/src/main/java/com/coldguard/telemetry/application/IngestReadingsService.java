package com.coldguard.telemetry.application;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.Anomaly;
import com.coldguard.telemetry.domain.IneligibilityReason;
import com.coldguard.telemetry.domain.ReadingEvaluator;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingests a batch of readings: validates each one, resolves what Asset knows about their sensors
 * (before touching the database, so an unreachable Asset stores nothing), and then, in one
 * transaction, stores every new reading as evidence and evaluates the eligible ones.
 *
 * <p>Ingestion is idempotent per reading id: a reading seen before is reported as a duplicate and
 * neither re-evaluated nor re-announced. One invalid reading never aborts the others. The readings
 * of one sensor are evaluated under a lock on its condition, in order of {@code recorded_at}, and
 * the sensors of a batch are taken in a stable order so concurrent batches cannot deadlock.
 *
 * <p>Only the simulator may ingest simulated readings and only an administrator test injections, so
 * the recorded source cannot be forged.
 */
public class IngestReadingsService {

  private static final Logger log = LoggerFactory.getLogger(IngestReadingsService.class);
  private static final String SIMULATOR_CALLER = "system:sensor-simulator";
  private static final BigDecimal VALUE_LIMIT = new BigDecimal("99999.999");

  private final SensorContexts contexts;
  private final ReadingRepository readings;
  private final SensorConditionRepository conditions;
  private final DomainEventPublisher events;
  private final IngestMetrics metrics;
  private final IngestSettings settings;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public IngestReadingsService(
      SensorContexts contexts,
      ReadingRepository readings,
      SensorConditionRepository conditions,
      DomainEventPublisher events,
      IngestMetrics metrics,
      IngestSettings settings,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.contexts = contexts;
    this.readings = readings;
    this.conditions = conditions;
    this.events = events;
    this.metrics = metrics;
    this.settings = settings;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /** One valid reading, parsed. */
  private record Parsed(
      int position, UUID id, UUID sensorId, Instant recordedAt, BigDecimal value, String unit) {}

  public List<ReadingResult> ingest(
      Actor actor, ReadingSource source, List<IncomingReading> batch) {
    authorize(actor, source);
    if (batch.size() > settings.maxBatchSize()) {
      throw new BatchTooLargeException(batch.size(), settings.maxBatchSize());
    }
    Instant receivedAt = clock.instant();
    ReadingResult[] results = new ReadingResult[batch.size()];
    Map<UUID, List<Parsed>> bySensor = new TreeMap<>();
    Set<UUID> seenInBatch = new HashSet<>();
    for (int i = 0; i < batch.size(); i++) {
      IncomingReading incoming = batch.get(i);
      Parsed parsed = parse(i, incoming, receivedAt, results);
      if (parsed == null) {
        continue;
      }
      if (!seenInBatch.add(parsed.id())) {
        results[i] = ReadingResult.duplicate(incoming.readingId());
        continue;
      }
      bySensor.computeIfAbsent(parsed.sensorId(), id -> new ArrayList<>()).add(parsed);
    }

    // Outside the transaction: a slow or absent Asset must not hold a database connection, and it
    // must fail the whole request before anything is stored.
    Map<UUID, SensorContext> known =
        bySensor.isEmpty() ? Map.of() : contexts.resolve(bySensor.keySet());

    Map<UUID, List<Parsed>> toStore = new LinkedHashMap<>();
    bySensor.forEach(
        (sensorId, group) -> {
          SensorContext context = known.get(sensorId);
          List<Parsed> accepted = new ArrayList<>();
          for (Parsed parsed : group) {
            String rejection = rejectionFor(parsed, context);
            if (rejection != null) {
              results[parsed.position()] =
                  ReadingResult.rejected(batch.get(parsed.position()).readingId(), rejection);
            } else {
              accepted.add(parsed);
            }
          }
          if (!accepted.isEmpty()) {
            toStore.put(sensorId, accepted);
          }
        });

    if (!toStore.isEmpty()) {
      EventActor eventActor = eventActor(actor);
      transaction.executeWithoutResult(
          status -> store(source, toStore, known, receivedAt, eventActor, batch, results));
    }
    for (ReadingResult result : results) {
      metrics.record(source, result.outcome(), result.eligible(), result.breached());
    }
    log.debug("Ingested {} readings from {}", results.length, source);
    return List.of(results);
  }

  private void store(
      ReadingSource source,
      Map<UUID, List<Parsed>> toStore,
      Map<UUID, SensorContext> known,
      Instant receivedAt,
      EventActor eventActor,
      List<IncomingReading> batch,
      ReadingResult[] results) {
    String correlationId = CorrelationContext.current().orElse(null);
    toStore.forEach(
        (sensorId, group) -> {
          SensorContext context = known.get(sensorId);
          SensorCondition locked = conditions.lockOrCreate(context, receivedAt);
          boolean reconnected = locked.reconnected();
          SensorCondition condition = locked.seen(context, receivedAt);
          Set<UUID> existing = readings.findExisting(group.stream().map(Parsed::id).toList());

          List<Parsed> ordered = new ArrayList<>(group);
          ordered.sort(Comparator.comparing(Parsed::recordedAt).thenComparing(Parsed::id));
          List<StoredReading> inserts = new ArrayList<>();
          for (Parsed parsed : ordered) {
            if (existing.contains(parsed.id())) {
              results[parsed.position()] =
                  ReadingResult.duplicate(batch.get(parsed.position()).readingId());
              continue;
            }
            IneligibilityReason ineligible = context.ineligibility();
            Anomaly anomaly = null;
            if (ineligible == null) {
              Optional<Anomaly> evaluated =
                  ReadingEvaluator.evaluate(parsed.value(), context.profile());
              SensorCondition.Step step =
                  condition.evaluate(parsed.recordedAt(), evaluated, context.profile());
              condition = step.condition();
              anomaly = evaluated.orElse(null);
              if (anomaly != null && !step.late()) {
                events.publish(
                    TelemetryEvents.thresholdBreached(
                        parsed.id(),
                        context,
                        parsed.value(),
                        parsed.unit(),
                        anomaly,
                        step.persistent(),
                        parsed.recordedAt(),
                        eventActor));
              }
            }
            inserts.add(
                new StoredReading(
                    parsed.id(),
                    sensorId,
                    context.assetId(),
                    parsed.recordedAt(),
                    receivedAt,
                    parsed.value(),
                    parsed.unit(),
                    source,
                    ineligible == null,
                    ineligible,
                    anomaly != null,
                    anomaly == null ? null : anomaly.type(),
                    anomaly == null ? null : anomaly.magnitude(),
                    correlationId));
            results[parsed.position()] =
                new ReadingResult(
                    batch.get(parsed.position()).readingId(),
                    ReadingOutcome.ACCEPTED,
                    ineligible == null,
                    anomaly != null,
                    null);
          }
          readings.insertAll(inserts);
          conditions.save(condition);
          if (reconnected) {
            log.info("Sensor {} is reporting again after losing connectivity", sensorId);
          }
        });
  }

  /**
   * @return null when the reading was rejected (its result is already recorded)
   */
  private Parsed parse(
      int position, IncomingReading in, Instant receivedAt, ReadingResult[] results) {
    String id = in.readingId();
    UUID readingId = uuid(id);
    if (readingId == null) {
      results[position] = ReadingResult.rejected(id, "INVALID_READING_ID");
      return null;
    }
    UUID sensorId = uuid(in.sensorId());
    if (sensorId == null) {
      results[position] = ReadingResult.rejected(id, "INVALID_SENSOR_ID");
      return null;
    }
    if (in.recordedAt() == null) {
      results[position] = ReadingResult.rejected(id, "INVALID_RECORDED_AT");
      return null;
    }
    if (in.recordedAt().isAfter(receivedAt.plus(settings.futureTolerance()))) {
      results[position] = ReadingResult.rejected(id, "RECORDED_AT_IN_FUTURE");
      return null;
    }
    if (in.unit() == null || in.unit().isBlank() || in.unit().strip().length() > 20) {
      results[position] = ReadingResult.rejected(id, "INVALID_UNIT");
      return null;
    }
    if (Double.isNaN(in.value()) || Double.isInfinite(in.value())) {
      results[position] = ReadingResult.rejected(id, "INVALID_VALUE");
      return null;
    }
    BigDecimal value = BigDecimal.valueOf(in.value()).setScale(3, RoundingMode.HALF_UP);
    if (value.abs().compareTo(VALUE_LIMIT) > 0) {
      results[position] = ReadingResult.rejected(id, "INVALID_VALUE");
      return null;
    }
    return new Parsed(position, readingId, sensorId, in.recordedAt(), value, in.unit().strip());
  }

  private static String rejectionFor(Parsed parsed, SensorContext context) {
    if (context == null) {
      return "SENSOR_NOT_FOUND";
    }
    if (context.profile() != null && !context.profile().unit().equalsIgnoreCase(parsed.unit())) {
      return "UNIT_MISMATCH";
    }
    return null;
  }

  private static UUID uuid(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException notAnId) {
      return null;
    }
  }

  private static void authorize(Actor actor, ReadingSource source) {
    if (source == null) {
      throw new IllegalArgumentException("source is required");
    }
    boolean simulator = actor != null && SIMULATOR_CALLER.equals(actor.id());
    boolean administrator = actor != null && actor.hasRole(Role.PLATFORM_ADMIN);
    boolean allowed =
        (simulator && source == ReadingSource.SIMULATOR)
            || (administrator && source == ReadingSource.TEST_INJECTION);
    if (!allowed) {
      throw new TelemetryAccessDeniedException(
          "Only the sensor simulator may ingest simulated readings and only a platform"
              + " administrator test injections");
    }
  }

  private static EventActor eventActor(Actor actor) {
    return actor.id().startsWith("system:")
        ? EventActor.system(actor.id().substring("system:".length()))
        : EventActor.user(actor.id());
  }
}
