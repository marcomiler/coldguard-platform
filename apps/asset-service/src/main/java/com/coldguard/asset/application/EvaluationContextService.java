package com.coldguard.asset.application;

import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.commons.security.Actor;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only projection for internal callers (Telemetry); never exposed to users. Built by {@code
 * AssetConfiguration} because its batch limit comes from configuration.
 */
public class EvaluationContextService {

  private final EvaluationContextRepository contexts;
  private final int maxBatch;

  public EvaluationContextService(EvaluationContextRepository contexts, int maxBatch) {
    this.contexts = contexts;
    this.maxBatch = maxBatch;
  }

  @Transactional(readOnly = true)
  public SensorEvaluationContext get(Actor caller, UUID sensorId) {
    AccessPolicy.requireSystemCaller(caller);
    return contexts.findByIds(List.of(sensorId)).stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
  }

  /** Sensors that do not exist are omitted from the result. */
  @Transactional(readOnly = true)
  public List<SensorEvaluationContext> getMany(Actor caller, List<UUID> sensorIds) {
    AccessPolicy.requireSystemCaller(caller);
    List<UUID> distinct = sensorIds.stream().distinct().toList();
    if (distinct.size() > maxBatch) {
      throw new IllegalArgumentException("at most " + maxBatch + " sensors per request");
    }
    return distinct.isEmpty() ? List.of() : contexts.findByIds(distinct);
  }
}
