package com.coldguard.asset.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.asset")
public record AssetProperties(
    @DefaultValue Calibration calibration,
    @DefaultValue Page page,
    @DefaultValue EvaluationContext evaluationContext,
    @DefaultValue CalibrationExpiry calibrationExpiry) {

  /** {@code defaultValidity} stays null when unset: no value is invented. */
  public record Calibration(Duration defaultValidity) {}

  /** {@code batchSize}: sensors read per query by the calibration expiry job. */
  public record CalibrationExpiry(@DefaultValue("100") int batchSize) {}

  /** Most sensors an internal caller may ask for in one request. */
  public record EvaluationContext(@DefaultValue("500") int maxBatch) {}

  public record Page(@DefaultValue("100") int maxSize, @DefaultValue("20") int defaultSize) {}
}
