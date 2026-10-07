package com.coldguard.simulator.config;

import com.coldguard.simulator.domain.Scenario;
import com.coldguard.simulator.domain.SensorSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the configured sensors into specs, failing at startup with every problem at once: a
 * scenario that silently did nothing would make a demo look broken for no visible reason.
 */
public final class ScenarioValidator {

  private ScenarioValidator() {}

  public static List<SensorSpec> validate(List<SimulatorProperties.Sensor> sensors) {
    List<String> problems = new ArrayList<>();
    List<SensorSpec> specs = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < sensors.size(); i++) {
      SimulatorProperties.Sensor s = sensors.get(i);
      String where = "sensors[" + i + "]";
      if (s.sensorId() == null || s.sensorId().isBlank() || !isUuid(s.sensorId())) {
        problems.add(where + ": sensor-id must be a UUID");
      } else if (!seen.add(s.sensorId())) {
        problems.add(where + ": sensor-id " + s.sensorId() + " is repeated");
      }
      if (s.interval() == null || s.interval().isZero() || s.interval().isNegative()) {
        problems.add(where + ": interval must be positive");
      }
      if (s.noise() < 0) {
        problems.add(where + ": noise must not be negative");
      }
      if (s.startAfter() != null && s.startAfter().isNegative()) {
        problems.add(where + ": start-after must not be negative");
      }
      checkParameters(where, s, problems);
      if (problems.isEmpty() || !mentions(problems, where)) {
        specs.add(
            new SensorSpec(
                s.sensorId(),
                s.unit(),
                s.interval(),
                s.baseline(),
                s.noise(),
                s.scenario(),
                s.breachValue(),
                s.startAfter(),
                s.breachReadings() == null ? 1 : s.breachReadings(),
                s.silenceDuration() == null ? Duration.ZERO : s.silenceDuration()));
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Invalid simulator scenario:\n - " + String.join("\n - ", problems));
    }
    return List.copyOf(specs);
  }

  private static void checkParameters(
      String where, SimulatorProperties.Sensor s, List<String> problems) {
    Scenario scenario = s.scenario();
    boolean needsBreach =
        scenario == Scenario.OUT_OF_RANGE
            || scenario == Scenario.RECOVERY
            || scenario == Scenario.PERSISTENCE;
    if (needsBreach && s.breachValue() == null) {
      problems.add(where + ": scenario " + scenario + " needs breach-value");
    }
    if (!needsBreach && s.breachValue() != null) {
      problems.add(where + ": scenario " + scenario + " does not use breach-value");
    }
    if (scenario == Scenario.RECOVERY) {
      if (s.breachReadings() == null || s.breachReadings() < 1) {
        problems.add(where + ": scenario RECOVERY needs breach-readings of at least 1");
      }
    } else if (s.breachReadings() != null) {
      problems.add(where + ": scenario " + scenario + " does not use breach-readings");
    }
    if (scenario == Scenario.CONNECTIVITY_LOSS) {
      if (s.silenceDuration() == null
          || s.silenceDuration().isZero()
          || s.silenceDuration().isNegative()) {
        problems.add(where + ": scenario CONNECTIVITY_LOSS needs a positive silence-duration");
      }
    } else if (s.silenceDuration() != null) {
      problems.add(where + ": scenario " + scenario + " does not use silence-duration");
    }
  }

  private static boolean mentions(List<String> problems, String where) {
    return problems.stream().anyMatch(p -> p.startsWith(where));
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
