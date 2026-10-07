package com.coldguard.simulator.domain;

import java.time.Duration;
import java.util.OptionalDouble;
import java.util.random.RandomGenerator;

/**
 * The value a sensor reports at a given moment, per scenario. Stateful (it counts what it already
 * emitted) and driven only by elapsed time and its own random source, so the same seed gives the
 * same sequence of values.
 */
public sealed interface SensorBehavior {

  /**
   * @param elapsed time since the simulator started
   * @return the value to report, or empty when the sensor is silent
   */
  OptionalDouble next(Duration elapsed);

  static SensorBehavior of(SensorSpec spec, RandomGenerator random) {
    Nominal nominal = new Nominal(spec.baseline(), spec.noise(), random);
    return switch (spec.scenario()) {
      case NOMINAL -> nominal;
      case OUT_OF_RANGE -> new OutOfRange(nominal, spec.breachValue(), spec.startAfter());
      case RECOVERY ->
          new Recovery(nominal, spec.breachValue(), spec.startAfter(), spec.breachReadings());
      case PERSISTENCE -> new Persistence(nominal, spec.breachValue(), spec.startAfter());
      case CONNECTIVITY_LOSS ->
          new ConnectivityLoss(nominal, spec.startAfter(), spec.silenceDuration());
    };
  }

  /** {@code baseline ± noise}, rounded to two decimals. */
  record Nominal(double baseline, double noise, RandomGenerator random) implements SensorBehavior {

    @Override
    public OptionalDouble next(Duration elapsed) {
      double variation = noise == 0 ? 0 : (random.nextDouble() * 2 - 1) * noise;
      return OptionalDouble.of(Math.round((baseline + variation) * 100.0) / 100.0);
    }
  }

  final class OutOfRange implements SensorBehavior {
    private final Nominal nominal;
    private final double breachValue;
    private final Duration startAfter;
    private boolean fired;

    OutOfRange(Nominal nominal, double breachValue, Duration startAfter) {
      this.nominal = nominal;
      this.breachValue = breachValue;
      this.startAfter = startAfter;
    }

    @Override
    public OptionalDouble next(Duration elapsed) {
      if (!fired && elapsed.compareTo(startAfter) >= 0) {
        fired = true;
        return OptionalDouble.of(breachValue);
      }
      return nominal.next(elapsed);
    }
  }

  final class Recovery implements SensorBehavior {
    private final Nominal nominal;
    private final double breachValue;
    private final Duration startAfter;
    private final int breachReadings;
    private int emitted;

    Recovery(Nominal nominal, double breachValue, Duration startAfter, int breachReadings) {
      this.nominal = nominal;
      this.breachValue = breachValue;
      this.startAfter = startAfter;
      this.breachReadings = breachReadings;
    }

    @Override
    public OptionalDouble next(Duration elapsed) {
      if (elapsed.compareTo(startAfter) >= 0 && emitted < breachReadings) {
        emitted++;
        return OptionalDouble.of(breachValue);
      }
      return nominal.next(elapsed);
    }
  }

  record Persistence(Nominal nominal, double breachValue, Duration startAfter)
      implements SensorBehavior {

    @Override
    public OptionalDouble next(Duration elapsed) {
      return elapsed.compareTo(startAfter) >= 0
          ? OptionalDouble.of(breachValue)
          : nominal.next(elapsed);
    }
  }

  record ConnectivityLoss(Nominal nominal, Duration startAfter, Duration silenceDuration)
      implements SensorBehavior {

    @Override
    public OptionalDouble next(Duration elapsed) {
      boolean silent =
          elapsed.compareTo(startAfter) >= 0
              && elapsed.compareTo(startAfter.plus(silenceDuration)) < 0;
      return silent ? OptionalDouble.empty() : nominal.next(elapsed);
    }
  }
}
