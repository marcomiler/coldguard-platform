package com.coldguard.telemetry.application;

import java.time.Duration;

/** Limits of one ingestion request. */
public record IngestSettings(int maxBatchSize, Duration futureTolerance) {

  public IngestSettings {
    if (maxBatchSize < 1) {
      throw new IllegalArgumentException("maxBatchSize must be at least 1");
    }
    if (futureTolerance == null || futureTolerance.isNegative()) {
      throw new IllegalArgumentException("futureTolerance must not be negative");
    }
  }
}
