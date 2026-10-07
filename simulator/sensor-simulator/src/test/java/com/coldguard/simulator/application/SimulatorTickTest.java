package com.coldguard.simulator.application;

import static com.coldguard.simulator.application.ReadingBufferTest.readings;
import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.simulator.application.ReadingSender.Outcome;
import com.coldguard.simulator.application.ReadingSender.Rejection;
import com.coldguard.simulator.domain.PendingReading;
import com.coldguard.simulator.domain.ReadingPlanner;
import com.coldguard.simulator.domain.Scenario;
import com.coldguard.simulator.domain.SensorSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SimulatorTickTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  /** A clock the test moves by hand. */
  private static final class Manual extends Clock {
    Instant now = T0;

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static final class Counting implements SimulatorMetrics {
    int sent;
    int dropped;
    int rejected;

    @Override
    public void sent(int count) {
      sent += count;
    }

    @Override
    public void dropped(int count) {
      dropped += count;
    }

    @Override
    public void rejected(int count) {
      rejected += count;
    }
  }

  private final Manual clock = new Manual();
  private final Counting metrics = new Counting();
  private final List<List<PendingReading>> sentBatches = new ArrayList<>();
  private Function<List<PendingReading>, Outcome> answer =
      batch -> new Outcome.Delivered(batch.size(), 0, List.of());

  private SimulatorTick tick(int capacity, int maxBatch) {
    SensorSpec spec =
        new SensorSpec(
            "00000000-0000-0000-0000-000000000001",
            "CELSIUS",
            Duration.ofSeconds(1),
            4.0,
            0.0,
            Scenario.NOMINAL,
            null,
            Duration.ZERO,
            1,
            Duration.ZERO);
    return new SimulatorTick(
        new ReadingPlanner(List.of(spec), 1L, T0),
        new ReadingBuffer(capacity),
        batch -> {
          sentBatches.add(List.copyOf(batch));
          return answer.apply(batch);
        },
        metrics,
        clock,
        maxBatch,
        Duration.ofSeconds(2),
        Duration.ofSeconds(8));
  }

  private void advance(long seconds) {
    clock.now = clock.now.plusSeconds(seconds);
  }

  @Test
  void deliversWhatIsDueEveryTick() {
    SimulatorTick tick = tick(100, 50);

    tick.run();
    advance(1);
    tick.run();

    assertThat(sentBatches).hasSize(2);
    assertThat(metrics.sent).isEqualTo(2);
  }

  @Test
  void whenTelemetryIsDownReadingsAreKeptAndResentWithTheSameIds() {
    SimulatorTick tick = tick(100, 50);
    answer = batch -> new Outcome.Unavailable("UNAVAILABLE");
    tick.run();
    List<PendingReading> firstAttempt = sentBatches.get(0);

    answer = batch -> new Outcome.Delivered(batch.size(), 0, List.of());
    advance(2);
    tick.run();

    List<PendingReading> afterwards = sentBatches.get(sentBatches.size() - 1);
    assertThat(afterwards).containsAll(firstAttempt);
    assertThat(afterwards).hasSizeGreaterThan(firstAttempt.size());
    assertThat(metrics.sent).isEqualTo(afterwards.size());
  }

  @Test
  void itBacksOffExponentiallyAndStopsHammeringAnUnavailableTelemetry() {
    SimulatorTick tick = tick(1000, 50);
    answer = batch -> new Outcome.Unavailable("UNAVAILABLE");

    tick.run(); // attempt 1 at t=0, next not before t=2
    advance(1);
    tick.run(); // inside the backoff: no attempt
    assertThat(sentBatches).hasSize(1);

    advance(1);
    tick.run(); // t=2: attempt 2, backoff doubles to 4 -> next not before t=6
    assertThat(sentBatches).hasSize(2);
    advance(3);
    tick.run(); // t=5
    assertThat(sentBatches).hasSize(2);
    advance(1);
    tick.run(); // t=6: attempt 3
    assertThat(sentBatches).hasSize(3);
  }

  @Test
  void aBoundedBufferDropsTheOldestWhileTelemetryIsAway() {
    SimulatorTick tick = tick(3, 50);
    answer = batch -> new Outcome.Unavailable("UNAVAILABLE");

    for (int i = 0; i < 6; i++) {
      tick.run();
      advance(1);
    }

    assertThat(metrics.dropped).isEqualTo(3);
  }

  @Test
  void rejectedReadingsAreReportedNeverRetried() {
    SimulatorTick tick = tick(100, 50);
    answer =
        batch ->
            new Outcome.Delivered(
                0,
                0,
                List.of(new Rejection("00000000-0000-0000-0000-000000000001", "UNIT_MISMATCH")));

    tick.run();
    advance(1);
    tick.run();

    assertThat(metrics.rejected).isEqualTo(2);
    assertThat(metrics.sent).isZero();
    assertThat(sentBatches.get(1)).hasSize(1);
  }

  @Test
  void aRefusedBatchIsDroppedNotRetriedForever() {
    SimulatorTick tick = tick(100, 50);
    answer = batch -> new Outcome.Refused("PERMISSION_DENIED");

    tick.run();
    advance(1);
    tick.run();

    assertThat(metrics.dropped).isEqualTo(2);
    assertThat(sentBatches.get(1)).hasSize(1);
  }

  @Test
  void aLargeBacklogIsSentInBatchesOfTheConfiguredSize() {
    SimulatorTick tick = tick(1000, 2);
    answer = batch -> new Outcome.Unavailable("UNAVAILABLE");
    for (int i = 0; i < 5; i++) {
      tick.run();
      advance(1);
    }
    sentBatches.clear();
    answer = batch -> new Outcome.Delivered(batch.size(), 0, List.of());
    advance(60);

    tick.run();

    assertThat(sentBatches).isNotEmpty();
    assertThat(sentBatches).allSatisfy(batch -> assertThat(batch.size()).isLessThanOrEqualTo(2));
    assertThat(sentBatches.stream().mapToInt(List::size).sum()).isGreaterThanOrEqualTo(5);
    assertThat(readings(1)).hasSize(1);
  }
}
