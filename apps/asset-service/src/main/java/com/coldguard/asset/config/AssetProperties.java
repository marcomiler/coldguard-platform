package com.coldguard.asset.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.asset")
public record AssetProperties(
    @DefaultValue Calibration calibration,
    @DefaultValue Page page,
    @DefaultValue EvaluationContext evaluationContext) {

  /** {@code defaultValidity} stays null when unset: no value is invented. */
  public record Calibration(Duration defaultValidity) {}

  /** Most sensors an internal caller may ask for in one request. */
  public record EvaluationContext(@DefaultValue("500") int maxBatch) {}

  public record Page(@DefaultValue("100") int maxSize, @DefaultValue("20") int defaultSize) {}
}
