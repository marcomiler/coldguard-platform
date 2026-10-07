package com.coldguard.simulator.application;

import com.coldguard.simulator.domain.PendingReading;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Readings not yet delivered, oldest first, with a fixed capacity so memory stays bounded while
 * Telemetry is away. When full, the oldest are dropped: the newest readings are the most useful.
 */
public final class ReadingBuffer {

  private final int capacity;
  private final Deque<PendingReading> pending = new ArrayDeque<>();

  public ReadingBuffer(int capacity) {
    this.capacity = capacity;
  }

  /**
   * @return how many old readings were dropped to make room
   */
  public int add(List<PendingReading> readings) {
    int dropped = 0;
    for (PendingReading reading : readings) {
      if (pending.size() == capacity) {
        pending.pollFirst();
        dropped++;
      }
      pending.addLast(reading);
    }
    return dropped;
  }

  /** The oldest readings, up to {@code max}, left in place until {@link #remove}. */
  public List<PendingReading> peek(int max) {
    List<PendingReading> batch = new ArrayList<>(Math.min(max, pending.size()));
    for (PendingReading reading : pending) {
      if (batch.size() == max) {
        break;
      }
      batch.add(reading);
    }
    return batch;
  }

  public void remove(int count) {
    for (int i = 0; i < count; i++) {
      pending.pollFirst();
    }
  }

  public int size() {
    return pending.size();
  }
}
