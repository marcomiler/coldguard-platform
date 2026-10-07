package com.coldguard.simulator.domain;

import static com.coldguard.simulator.domain.DomainFixtures.spec;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.OptionalDouble;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SensorBehaviorTest {

  private static SensorBehavior behavior(Scenario scenario) {
    return SensorBehavior.of(spec(scenario), new Random(42));
  }

  private static Duration s(long seconds) {
    return Duration.ofSeconds(seconds);
  }

  @Test
  void nominal_staysWithinBaselinePlusMinusNoise() {
    SensorBehavior nominal = behavior(Scenario.NOMINAL);

    for (int i = 0; i < 500; i++) {
      assertThat(nominal.next(s(i)).orElseThrow()).isBetween(3.7, 4.3);
    }
  }

  @Test
  void outOfRange_emitsOneBreachAfterStartThenReturnsToNominal() {
    SensorBehavior behavior = behavior(Scenario.OUT_OF_RANGE);

    assertThat(behavior.next(s(10)).orElseThrow()).isBetween(3.7, 4.3);
    assertThat(behavior.next(s(20)).orElseThrow()).isEqualTo(11.5);
    assertThat(behavior.next(s(25)).orElseThrow()).isBetween(3.7, 4.3);
    assertThat(behavior.next(s(30)).orElseThrow()).isBetween(3.7, 4.3);
  }

  @Test
  void recovery_emitsTheConfiguredNumberOfBreachesThenNominal() {
    SensorBehavior behavior = behavior(Scenario.RECOVERY);

    assertThat(behavior.next(s(19)).orElseThrow()).isBetween(3.7, 4.3);
    assertThat(behavior.next(s(20)).orElseThrow()).isEqualTo(11.5);
    assertThat(behavior.next(s(25)).orElseThrow()).isEqualTo(11.5);
    assertThat(behavior.next(s(30)).orElseThrow()).isBetween(3.7, 4.3);
  }

  @Test
  void persistence_sustainsTheBreachOnceStarted() {
    SensorBehavior behavior = behavior(Scenario.PERSISTENCE);

    assertThat(behavior.next(s(15)).orElseThrow()).isBetween(3.7, 4.3);
    for (int seconds = 20; seconds < 400; seconds += 5) {
      assertThat(behavior.next(s(seconds)).orElseThrow()).isEqualTo(11.5);
    }
  }

  @Test
  void connectivityLoss_isSilentOnlyDuringTheSilenceWindow() {
    SensorBehavior behavior = behavior(Scenario.CONNECTIVITY_LOSS);

    assertThat(behavior.next(s(19)).isPresent()).isTrue();
    assertThat(behavior.next(s(20))).isEqualTo(OptionalDouble.empty());
    assertThat(behavior.next(s(79))).isEqualTo(OptionalDouble.empty());
    assertThat(behavior.next(s(80)).isPresent()).isTrue();
  }

  @Test
  void theSameSeedGivesTheSameSequence() {
    SensorBehavior a = SensorBehavior.of(spec(Scenario.NOMINAL), new Random(7));
    SensorBehavior b = SensorBehavior.of(spec(Scenario.NOMINAL), new Random(7));

    for (int i = 0; i < 50; i++) {
      assertThat(a.next(s(i))).isEqualTo(b.next(s(i)));
    }
  }
}
