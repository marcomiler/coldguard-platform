package com.coldguard.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.simulator.domain.Scenario;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The scenario file as YAML-style properties: defaults, durations, and validation of the shape. */
class SimulatorPropertiesBindingTest {

  @Configuration
  @EnableConfigurationProperties(SimulatorProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void bindsASensorWithItsDefaults() {
    runner
        .withPropertyValues(
            "coldguard.simulator.sensors[0].sensor-id=00000000-0000-0000-0000-000000000001",
            "coldguard.simulator.sensors[0].baseline=4.0",
            "coldguard.simulator.sensors[1].sensor-id=00000000-0000-0000-0000-000000000002",
            "coldguard.simulator.sensors[1].baseline=-20",
            "coldguard.simulator.sensors[1].scenario=PERSISTENCE",
            "coldguard.simulator.sensors[1].breach-value=-10",
            "coldguard.simulator.sensors[1].start-after=30s",
            "coldguard.simulator.random-seed=42")
        .run(
            context -> {
              SimulatorProperties properties = context.getBean(SimulatorProperties.class);
              assertThat(properties.randomSeed()).isEqualTo(42L);
              assertThat(properties.tickInterval()).isEqualTo(Duration.ofSeconds(1));
              assertThat(properties.maxBatchSize()).isEqualTo(200);
              assertThat(properties.buffer().maxPendingReadings()).isEqualTo(5000);
              assertThat(properties.sensors()).hasSize(2);
              assertThat(properties.sensors().get(0).scenario()).isEqualTo(Scenario.NOMINAL);
              assertThat(properties.sensors().get(0).interval()).isEqualTo(Duration.ofSeconds(5));
              assertThat(properties.sensors().get(0).unit()).isEqualTo("CELSIUS");
              assertThat(properties.sensors().get(1).startAfter())
                  .isEqualTo(Duration.ofSeconds(30));
              assertThat(ScenarioValidator.validate(properties.sensors())).hasSize(2);
            });
  }

  @Test
  void aSensorWithoutIdDoesNotStart() {
    runner
        .withPropertyValues("coldguard.simulator.sensors[0].baseline=4.0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void anUnknownScenarioDoesNotStart() {
    runner
        .withPropertyValues(
            "coldguard.simulator.sensors[0].sensor-id=00000000-0000-0000-0000-000000000001",
            "coldguard.simulator.sensors[0].baseline=4.0",
            "coldguard.simulator.sensors[0].scenario=EXPLODE")
        .run(context -> assertThat(context).hasFailed());
  }
}
