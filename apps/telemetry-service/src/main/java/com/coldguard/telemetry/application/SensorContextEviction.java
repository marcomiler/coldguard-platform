package com.coldguard.telemetry.application;

import java.util.UUID;

/** Port to forget what is remembered about sensors, so the next reading asks Asset again. */
public interface SensorContextEviction {

  void evict(UUID sensorId);

  void evictAll();
}
