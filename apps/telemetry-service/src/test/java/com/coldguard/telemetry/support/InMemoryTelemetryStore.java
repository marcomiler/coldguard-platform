package com.coldguard.telemetry.support;

import com.coldguard.telemetry.application.ReadingCursor;
import com.coldguard.telemetry.application.ReadingRepository;
import com.coldguard.telemetry.application.SensorConditionRepository;
import com.coldguard.telemetry.application.SensorContexts;
import com.coldguard.telemetry.application.StoredReading;
import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The persistence and Asset ports over plain collections, recording how they were used. */
public class InMemoryTelemetryStore {

  public final Map<UUID, StoredReading> readings = new LinkedHashMap<>();
  public final Map<UUID, SensorCondition> conditions = new HashMap<>();

  /** What Asset "knows"; a sensor not in the map does not exist. */
  public final Map<UUID, SensorContext> assetContexts = new HashMap<>();

  /** The order in which sensors were locked, to check the lock order is stable. */
  public final List<UUID> lockOrder = new ArrayList<>();

  /** The sensor ids Asset was asked about, one entry per call. */
  public final List<Set<UUID>> assetCalls = new ArrayList<>();

  public RuntimeException assetFailure;

  public final SensorContexts contexts =
      sensorIds -> {
        assetCalls.add(new HashSet<>(sensorIds));
        if (assetFailure != null) {
          throw assetFailure;
        }
        Map<UUID, SensorContext> found = new HashMap<>();
        for (UUID id : sensorIds) {
          if (assetContexts.containsKey(id)) {
            found.put(id, assetContexts.get(id));
          }
        }
        return found;
      };

  public final ReadingRepository readingRepository =
      new ReadingRepository() {
        @Override
        public Set<UUID> findExisting(Collection<UUID> ids) {
          Set<UUID> existing = new HashSet<>();
          ids.forEach(
              id -> {
                if (readings.containsKey(id)) {
                  existing.add(id);
                }
              });
          return existing;
        }

        @Override
        public void insertAll(List<StoredReading> toInsert) {
          toInsert.forEach(reading -> readings.putIfAbsent(reading.id(), reading));
        }

        @Override
        public List<StoredReading> findPage(
            UUID sensorId, Instant from, Instant to, ReadingCursor after, int limit) {
          return readings.values().stream()
              .filter(r -> r.sensorId().equals(sensorId))
              .filter(r -> !r.recordedAt().isBefore(from) && r.recordedAt().isBefore(to))
              .filter(
                  r ->
                      after == null
                          || r.recordedAt().isBefore(after.recordedAt())
                          || (r.recordedAt().equals(after.recordedAt())
                              && r.id().compareTo(after.id()) < 0))
              .sorted(
                  Comparator.comparing(StoredReading::recordedAt)
                      .thenComparing(StoredReading::id)
                      .reversed())
              .limit(limit)
              .toList();
        }
      };

  public final SensorConditionRepository conditionRepository =
      new SensorConditionRepository() {
        @Override
        public SensorCondition lockOrCreate(SensorContext context, Instant receivedAt) {
          lockOrder.add(context.sensorId());
          return conditions.computeIfAbsent(
              context.sensorId(), id -> SensorCondition.first(context, receivedAt));
        }

        @Override
        public void save(SensorCondition condition) {
          conditions.put(condition.sensorId(), condition);
        }

        @Override
        public Optional<SensorCondition> lockExisting(UUID sensorId) {
          return Optional.ofNullable(conditions.get(sensorId));
        }

        @Override
        public List<SensorCondition> lockOverdue(Instant now, double toleranceFactor, int limit) {
          return conditions.values().stream()
              .filter(c -> c.connectivityLostAt() == null)
              .filter(c -> c.sensorStatus() == SensorStatus.ACTIVE)
              .filter(c -> c.expectedIntervalSeconds() > 0)
              .filter(
                  c ->
                      c.lastReadingAt()
                          .plusMillis((long) (c.expectedIntervalSeconds() * toleranceFactor * 1000))
                          .isBefore(now))
              .sorted(Comparator.comparing(SensorCondition::lastReadingAt))
              .limit(limit)
              .toList();
        }

        @Override
        public List<SensorCondition> findPage(boolean onlyLost, int page, int size) {
          return conditions.values().stream()
              .filter(c -> !onlyLost || c.connectivityLostAt() != null)
              .sorted(Comparator.comparing(SensorCondition::sensorId))
              .skip((long) page * size)
              .limit(size)
              .toList();
        }

        @Override
        public long count(boolean onlyLost) {
          return conditions.values().stream()
              .filter(c -> !onlyLost || c.connectivityLostAt() != null)
              .count();
        }
      };
}
