package com.coldguard.simulator.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Decides which readings are due. Each sensor is on its own interval; a sensor that is silent still
 * advances its schedule, so it resumes at its normal pace when the silence ends.
 */
public final class ReadingPlanner {

  private final Instant startedAt;
  private final List<Emitter> emitters = new ArrayList<>();

  private static final class Emitter {
    private final SensorSpec spec;
    private final SensorBehavior behavior;
    private Instant nextDue;

    Emitter(SensorSpec spec, SensorBehavior behavior, Instant first) {
      this.spec = spec;
      this.behavior = behavior;
      this.nextDue = first;
    }
  }

  /**
   * @param seed when present, makes the random part of every sensor reproducible
   */
  public ReadingPlanner(List<SensorSpec> sensors, Long seed, Instant startedAt) {
    this.startedAt = startedAt;
    for (SensorSpec spec : sensors) {
      RandomGenerator random =
          new java.util.Random(
              seed == null ? System.nanoTime() : seed ^ spec.sensorId().hashCode());
      emitters.add(new Emitter(spec, SensorBehavior.of(spec, random), startedAt));
    }
  }

  /** Readings whose moment has come at {@code now}, with a fresh id each. */
  public List<PendingReading> due(Instant now) {
    List<PendingReading> due = new ArrayList<>();
    Duration elapsed = Duration.between(startedAt, now);
    for (Emitter emitter : emitters) {
      if (emitter.nextDue.isAfter(now)) {
        continue;
      }
      OptionalDouble value = emitter.behavior.next(elapsed);
      emitter.nextDue = emitter.nextDue.plus(emitter.spec.interval());
      if (!emitter.nextDue.isAfter(now)) {
        // Far behind (a long pause): skip ahead rather than send a burst of old readings.
        emitter.nextDue = now.plus(emitter.spec.interval());
      }
      value.ifPresent(
          v ->
              due.add(
                  new PendingReading(
                      UUID.randomUUID(), emitter.spec.sensorId(), now, v, emitter.spec.unit())));
    }
    return due;
  }
}
