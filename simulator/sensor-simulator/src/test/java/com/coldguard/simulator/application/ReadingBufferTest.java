package com.coldguard.simulator.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.simulator.domain.PendingReading;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReadingBufferTest {

  static PendingReading reading(double value) {
    return new PendingReading(UUID.randomUUID(), "s", Instant.EPOCH, value, "CELSIUS");
  }

  static List<PendingReading> readings(int count) {
    return IntStream.range(0, count).mapToObj(i -> reading(i)).toList();
  }

  @Test
  void keepsReadingsInOrderUntilRemoved() {
    ReadingBuffer buffer = new ReadingBuffer(10);
    List<PendingReading> readings = readings(5);
    buffer.add(readings);

    assertThat(buffer.peek(3)).containsExactlyElementsOf(readings.subList(0, 3));
    assertThat(buffer.size()).isEqualTo(5);

    buffer.remove(3);

    assertThat(buffer.peek(10)).containsExactlyElementsOf(readings.subList(3, 5));
  }

  @Test
  void whenFullItDropsTheOldestAndCountsThem() {
    ReadingBuffer buffer = new ReadingBuffer(3);
    List<PendingReading> readings = readings(5);

    int dropped = buffer.add(readings);

    assertThat(dropped).isEqualTo(2);
    assertThat(buffer.size()).isEqualTo(3);
    assertThat(buffer.peek(10)).containsExactlyElementsOf(readings.subList(2, 5));
  }
}
