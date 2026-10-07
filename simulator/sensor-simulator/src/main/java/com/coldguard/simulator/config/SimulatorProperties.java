package com.coldguard.simulator.config;

import com.coldguard.simulator.domain.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The simulator's scenario file. Everything numeric is a demonstration placeholder; the seed is the
 * only source of the thresholds these values must agree with.
 */
@Validated
@ConfigurationProperties("coldguard.simulator")
public record SimulatorProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("1s") @NotNull Duration tickInterval,
    Long randomSeed,
    @DefaultValue("200") @Positive int maxBatchSize,
    @DefaultValue @Valid Buffer buffer,
    @DefaultValue("5s") @NotNull Duration deadline,
    @DefaultValue @Valid Backoff backoff,
    @DefaultValue List<@Valid Sensor> sensors) {

  public record Buffer(@DefaultValue("5000") @Positive int maxPendingReadings) {}

  public record Backoff(
      @DefaultValue("1s") @NotNull Duration initial, @DefaultValue("30s") @NotNull Duration max) {}

  /** One simulated sensor; which optional fields apply depends on the scenario. */
  public record Sensor(
      @NotBlank String sensorId,
      @DefaultValue("CELSIUS") @NotBlank String unit,
      @DefaultValue("5s") @NotNull Duration interval,
      @NotNull Double baseline,
      @DefaultValue("0.3") double noise,
      @DefaultValue("NOMINAL") @NotNull Scenario scenario,
      Double breachValue,
      @DefaultValue("0s") @NotNull Duration startAfter,
      Integer breachReadings,
      Duration silenceDuration) {}
}
