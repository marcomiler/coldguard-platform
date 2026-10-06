package com.coldguard.telemetry.infrastructure;

import com.coldguard.telemetry.application.SensorContexts;
import com.coldguard.telemetry.domain.SensorContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The evaluation context of each sensor, cached for a short time so a stream of readings does not
 * call Asset for every batch. The time limit bounds how stale a context can be if a change event is
 * late (eventual consistency); a sensor that does not exist is never cached.
 */
public class CachedSensorContexts implements SensorContexts {

  private final Cache<UUID, SensorContext> cache;
  private final AssetContextClient asset;

  public CachedSensorContexts(
      AssetContextClient asset, Duration timeToLive, long maxSize, Ticker ticker) {
    this.asset = asset;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(timeToLive)
            .maximumSize(maxSize)
            .ticker(ticker)
            .build();
  }

  @Override
  public Map<UUID, SensorContext> resolve(Collection<UUID> sensorIds) {
    Map<UUID, SensorContext> resolved = new HashMap<>();
    List<UUID> missing = new ArrayList<>();
    for (UUID id : sensorIds) {
      SensorContext cached = cache.getIfPresent(id);
      if (cached != null) {
        resolved.put(id, cached);
      } else {
        missing.add(id);
      }
    }
    if (!missing.isEmpty()) {
      for (SensorContext fetched : asset.fetch(missing)) {
        cache.put(fetched.sensorId(), fetched);
        resolved.put(fetched.sensorId(), fetched);
      }
    }
    return resolved;
  }

  /** Forgets one sensor, so its next reading asks Asset again. */
  public void evict(UUID sensorId) {
    cache.invalidate(sensorId);
  }

  /** Forgets everything. */
  public void evictAll() {
    cache.invalidateAll();
  }
}
