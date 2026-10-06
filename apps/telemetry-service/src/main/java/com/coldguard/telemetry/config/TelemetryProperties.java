package com.coldguard.telemetry.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.telemetry")
public record TelemetryProperties(
    @DefaultValue Ingest ingest,
    @DefaultValue EvaluationContextCache evaluationContextCache,
    @DefaultValue("2s") Duration assetCallDeadline,
    @DefaultValue ReadingsQuery readingsQuery,
    @DefaultValue Page page) {

  public record Ingest(
      @DefaultValue("500") int maxBatchSize, @DefaultValue("30s") Duration futureTolerance) {}

  public record EvaluationContextCache(
      @DefaultValue("60s") Duration ttl, @DefaultValue("10000") long maxSize) {}

  public record ReadingsQuery(@DefaultValue("7d") Duration maxRange) {}

  public record Page(@DefaultValue("100") int maxSize, @DefaultValue("20") int defaultSize) {}
}
