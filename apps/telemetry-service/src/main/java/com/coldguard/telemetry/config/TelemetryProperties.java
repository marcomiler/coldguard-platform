package com.coldguard.telemetry.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.telemetry")
public record TelemetryProperties(
    @DefaultValue Ingest ingest,
    @DefaultValue EvaluationContextCache evaluationContextCache,
    @DefaultValue("2s") Duration assetCallDeadline,
    @DefaultValue Connectivity connectivity,
    @DefaultValue ReadingsQuery readingsQuery,
    @DefaultValue Page page) {

  public record Ingest(
      @DefaultValue("500") int maxBatchSize, @DefaultValue("30s") Duration futureTolerance) {}

  public record EvaluationContextCache(
      @DefaultValue("60s") Duration ttl, @DefaultValue("10000") long maxSize) {}

  /**
   * {@code toleranceFactor} (at least 1) widens the expected interval before a silence counts as
   * lost connectivity, so jitter does not raise false alarms.
   */
  public record Connectivity(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("30s") Duration checkInterval,
      @DefaultValue("1.5") double toleranceFactor,
      @DefaultValue("100") int batchSize) {

    public Connectivity {
      if (toleranceFactor < 1 || batchSize < 1) {
        throw new IllegalArgumentException(
            "connectivity tolerance-factor must be at least 1 and batch-size positive");
      }
    }
  }

  public record ReadingsQuery(@DefaultValue("7d") Duration maxRange) {}

  public record Page(@DefaultValue("100") int maxSize, @DefaultValue("20") int defaultSize) {}
}
