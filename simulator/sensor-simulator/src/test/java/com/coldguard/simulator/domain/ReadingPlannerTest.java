package com.coldguard.simulator.domain;

import static com.coldguard.simulator.domain.DomainFixtures.SENSOR;
import static com.coldguard.simulator.domain.DomainFixtures.T0;
import static com.coldguard.simulator.domain.DomainFixtures.spec;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReadingPlannerTest {

  private static SensorSpec at(String id, Scenario scenario, Duration interval) {
    SensorSpec base = spec(scenario);
    return new SensorSpec(
        id,
        base.unit(),
        interval,
        base.baseline(),
        base.noise(),
        base.scenario(),
        base.breachValue(),
        base.startAfter(),
        base.breachReadings(),
        base.silenceDuration());
  }

  @Test
  void aSensorEmitsAtItsOwnInterval() {
    ReadingPlanner planner =
        new ReadingPlanner(
            List.of(
                at("00000000-0000-0000-0000-00000000000a", Scenario.NOMINAL, Duration.ofSeconds(5)),
                at(
                    "00000000-0000-0000-0000-00000000000b",
                    Scenario.NOMINAL,
                    Duration.ofSeconds(10))),
            1L,
            T0);

    int fast = 0;
    int slow = 0;
    for (int second = 0; second < 30; second++) {
      for (PendingReading r : planner.due(T0.plusSeconds(second))) {
        if (r.sensorId().endsWith("a")) {
          fast++;
        } else {
          slow++;
        }
      }
    }

    assertThat(fast).isEqualTo(6);
    assertThat(slow).isEqualTo(3);
  }

  @Test
  void everyReadingGetsAFreshIdAndTheMomentItWasGenerated() {
    ReadingPlanner planner = new ReadingPlanner(List.of(spec(Scenario.NOMINAL)), 1L, T0);

    List<PendingReading> all = new ArrayList<>();
    for (int second = 0; second < 30; second += 5) {
      all.addAll(planner.due(T0.plusSeconds(second)));
    }

    assertThat(all).hasSize(6);
    assertThat(all.stream().map(PendingReading::readingId).collect(Collectors.toSet())).hasSize(6);
    assertThat(all.get(1).recordedAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(all.get(0).unit()).isEqualTo("CELSIUS");
  }

  @Test
  void aSilentSensorResumesAtItsPaceAfterTheSilence() {
    ReadingPlanner planner = new ReadingPlanner(List.of(spec(Scenario.CONNECTIVITY_LOSS)), 1L, T0);

    List<Integer> emittedAt = new ArrayList<>();
    for (int second = 0; second <= 100; second++) {
      if (!planner.due(T0.plusSeconds(second)).isEmpty()) {
        emittedAt.add(second);
      }
    }

    assertThat(emittedAt).contains(0, 5, 15).doesNotContain(20, 25, 75);
    assertThat(emittedAt).contains(80, 85, 100);
  }

  @Test
  void afterALongPauseItDoesNotSendABurstOfOldReadings() {
    ReadingPlanner planner = new ReadingPlanner(List.of(spec(Scenario.NOMINAL)), 1L, T0);
    planner.due(T0);

    assertThat(planner.due(T0.plus(Duration.ofHours(1)))).hasSize(1);
    assertThat(planner.due(T0.plus(Duration.ofHours(1)).plusSeconds(1))).isEmpty();
  }

  @Test
  void withTheSameSeedTwoRunsProduceTheSameValues() {
    List<Double> first = values(42L);
    List<Double> second = values(42L);
    List<Double> other = values(43L);

    assertThat(first).isEqualTo(second);
    assertThat(first).isNotEqualTo(other);
  }

  private static List<Double> values(long seed) {
    ReadingPlanner planner =
        new ReadingPlanner(
            List.of(
                spec(Scenario.NOMINAL),
                at(
                    "00000000-0000-0000-0000-00000000000c",
                    Scenario.PERSISTENCE,
                    Duration.ofSeconds(5))),
            seed,
            T0);
    List<Double> values = new ArrayList<>();
    for (int second = 0; second < 60; second += 5) {
      planner.due(T0.plusSeconds(second)).forEach(r -> values.add(r.value()));
    }
    return values;
  }

  @Test
  void sensorIdsAreKept() {
    ReadingPlanner planner = new ReadingPlanner(List.of(spec(Scenario.NOMINAL)), null, T0);

    assertThat(planner.due(T0).stream().map(PendingReading::sensorId).collect(Collectors.toSet()))
        .isEqualTo(Set.of(SENSOR));
  }
}
