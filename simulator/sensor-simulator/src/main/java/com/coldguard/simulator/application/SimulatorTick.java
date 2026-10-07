package com.coldguard.simulator.application;

import com.coldguard.simulator.application.ReadingSender.Outcome;
import com.coldguard.simulator.application.ReadingSender.Rejection;
import com.coldguard.simulator.domain.PendingReading;
import com.coldguard.simulator.domain.ReadingPlanner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One step of the simulator: generate what is due, queue it, and deliver as much as possible. A
 * failed delivery keeps the readings (same ids) and waits with exponential backoff before trying
 * again; Telemetry deduplicates by reading id, so a resend is harmless. Not thread-safe: the
 * scheduler runs it one tick at a time.
 */
public class SimulatorTick {

  private static final Logger log = LoggerFactory.getLogger(SimulatorTick.class);

  private final ReadingPlanner planner;
  private final ReadingBuffer buffer;
  private final ReadingSender sender;
  private final SimulatorMetrics metrics;
  private final Clock clock;
  private final int maxBatchSize;
  private final Duration initialBackoff;
  private final Duration maxBackoff;

  private Duration backoff;
  private Instant retryNotBefore = Instant.MIN;

  public SimulatorTick(
      ReadingPlanner planner,
      ReadingBuffer buffer,
      ReadingSender sender,
      SimulatorMetrics metrics,
      Clock clock,
      int maxBatchSize,
      Duration initialBackoff,
      Duration maxBackoff) {
    this.planner = planner;
    this.buffer = buffer;
    this.sender = sender;
    this.metrics = metrics;
    this.clock = clock;
    this.maxBatchSize = maxBatchSize;
    this.initialBackoff = initialBackoff;
    this.maxBackoff = maxBackoff;
    this.backoff = initialBackoff;
  }

  public void run() {
    Instant now = clock.instant();
    int dropped = buffer.add(planner.due(now));
    if (dropped > 0) {
      metrics.dropped(dropped);
      log.warn("Buffer full: dropped {} oldest readings", dropped);
    }
    if (now.isBefore(retryNotBefore)) {
      return;
    }
    while (buffer.size() > 0) {
      List<PendingReading> batch = buffer.peek(maxBatchSize);
      Outcome outcome = sender.send(batch);
      switch (outcome) {
        case Outcome.Delivered delivered -> {
          buffer.remove(batch.size());
          metrics.sent(batch.size() - delivered.rejections().size());
          reportRejections(delivered.rejections());
          backoff = initialBackoff;
        }
        case Outcome.Unavailable unavailable -> {
          retryNotBefore = now.plus(backoff);
          log.warn(
              "Telemetry unavailable ({}); {} readings kept, next try in {}",
              unavailable.reason(),
              buffer.size(),
              backoff);
          backoff =
              backoff.multipliedBy(2).compareTo(maxBackoff) > 0
                  ? maxBackoff
                  : backoff.multipliedBy(2);
          return;
        }
        case Outcome.Refused refused -> {
          buffer.remove(batch.size());
          metrics.dropped(batch.size());
          log.error("Telemetry refused a batch of {} readings: {}", batch.size(), refused.reason());
        }
      }
    }
  }

  private void reportRejections(List<Rejection> rejections) {
    if (rejections.isEmpty()) {
      return;
    }
    metrics.rejected(rejections.size());
    // Sensor and code only, never the value.
    rejections.forEach(
        r -> log.warn("Reading rejected: sensor={} code={}", r.sensorId(), r.code()));
  }
}
