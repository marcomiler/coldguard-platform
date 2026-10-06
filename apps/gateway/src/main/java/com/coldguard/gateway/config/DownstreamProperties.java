package com.coldguard.gateway.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-service settings of the calls the Gateway makes, under {@code coldguard.gateway.downstream}.
 */
@ConfigurationProperties(prefix = "coldguard.gateway")
public record DownstreamProperties(@DefaultValue Map<String, Service> downstream) {

  private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(5);

  public record Service(Duration deadline) {}

  public Duration deadline(String service) {
    Service settings = downstream.get(service);
    return settings == null || settings.deadline() == null ? DEFAULT_DEADLINE : settings.deadline();
  }
}
