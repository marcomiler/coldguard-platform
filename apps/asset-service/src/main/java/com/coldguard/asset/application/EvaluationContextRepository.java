package com.coldguard.asset.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EvaluationContextRepository {

  /** One query over sensor, asset and profile; ids that match no sensor are simply absent. */
  List<SensorEvaluationContext> findByIds(Collection<UUID> sensorIds);
}
