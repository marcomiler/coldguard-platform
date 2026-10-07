package com.coldguard.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.simulator.domain.Scenario;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioValidatorTest {

  private static final String ID = "00000000-0000-0000-0000-000000000001";

  private static SimulatorProperties.Sensor sensor(
      String id,
      Scenario scenario,
      Double breachValue,
      Integer breachReadings,
      Duration silence,
      Duration interval) {
    return new SimulatorProperties.Sensor(
        id,
        "CELSIUS",
        interval,
        4.0,
        0.3,
        scenario,
        breachValue,
        Duration.ZERO,
        breachReadings,
        silence);
  }

  private static SimulatorProperties.Sensor ok(Scenario scenario) {
    return switch (scenario) {
      case NOMINAL -> sensor(ID, scenario, null, null, null, Duration.ofSeconds(5));
      case OUT_OF_RANGE, PERSISTENCE ->
          sensor(ID, scenario, 11.5, null, null, Duration.ofSeconds(5));
      case RECOVERY -> sensor(ID, scenario, 9.0, 2, null, Duration.ofSeconds(5));
      case CONNECTIVITY_LOSS ->
          sensor(ID, scenario, null, null, Duration.ofMinutes(5), Duration.ofSeconds(5));
    };
  }

  @Test
  void aValidSensorOfEveryScenarioIsAccepted() {
    for (Scenario scenario : Scenario.values()) {
      assertThat(ScenarioValidator.validate(List.of(ok(scenario)))).hasSize(1);
    }
  }

  @Test
  void aMissingOrMalformedSensorIdFailsFast() {
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor(null, Scenario.NOMINAL, null, null, null, Duration.ofSeconds(5)))))
        .hasMessageContaining("sensor-id must be a UUID");
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor("nope", Scenario.NOMINAL, null, null, null, Duration.ofSeconds(5)))))
        .hasMessageContaining("sensor-id must be a UUID");
  }

  @Test
  void aNonPositiveIntervalFailsFast() {
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(sensor(ID, Scenario.NOMINAL, null, null, null, Duration.ZERO))))
        .hasMessageContaining("interval must be positive");
  }

  @Test
  void aScenarioNeedsItsOwnParametersAndRejectsOthers() {
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor(ID, Scenario.PERSISTENCE, null, null, null, Duration.ofSeconds(5)))))
        .hasMessageContaining("needs breach-value");
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(sensor(ID, Scenario.NOMINAL, 9.0, null, null, Duration.ofSeconds(5)))))
        .hasMessageContaining("does not use breach-value");
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(sensor(ID, Scenario.RECOVERY, 9.0, null, null, Duration.ofSeconds(5)))))
        .hasMessageContaining("breach-readings");
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor(
                            ID,
                            Scenario.CONNECTIVITY_LOSS,
                            null,
                            null,
                            null,
                            Duration.ofSeconds(5)))))
        .hasMessageContaining("silence-duration");
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor(
                            ID,
                            Scenario.OUT_OF_RANGE,
                            9.0,
                            null,
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(5)))))
        .hasMessageContaining("does not use silence-duration");
  }

  @Test
  void aRepeatedSensorIsRejected() {
    assertThatThrownBy(
            () -> ScenarioValidator.validate(List.of(ok(Scenario.NOMINAL), ok(Scenario.NOMINAL))))
        .hasMessageContaining("repeated");
  }

  @Test
  void allProblemsAreReportedTogether() {
    assertThatThrownBy(
            () ->
                ScenarioValidator.validate(
                    List.of(
                        sensor("x", Scenario.PERSISTENCE, null, null, null, Duration.ZERO),
                        sensor(
                            ID,
                            Scenario.CONNECTIVITY_LOSS,
                            null,
                            null,
                            null,
                            Duration.ofSeconds(5)))))
        .hasMessageContaining("sensors[0]")
        .hasMessageContaining("sensors[1]");
  }
}
