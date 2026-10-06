package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.SensorContext;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Port to what Asset knows about sensors. A sensor that does not exist is absent from the map. */
public interface SensorContexts {

  /**
   * @throws AssetUnavailableException if Asset cannot answer for a sensor that is not cached
   */
  Map<UUID, SensorContext> resolve(Collection<UUID> sensorIds);
}
