package com.coldguard.simulator.infrastructure;

import com.coldguard.simulator.application.SimulatorMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerSimulatorMetrics implements SimulatorMetrics {

  private final Counter sent;
  private final Counter dropped;
  private final Counter rejected;

  public MicrometerSimulatorMetrics(MeterRegistry registry) {
    this.sent = Counter.builder("coldguard.simulator.readings.sent").register(registry);
    this.dropped = Counter.builder("coldguard.simulator.readings.dropped").register(registry);
    this.rejected = Counter.builder("coldguard.simulator.readings.rejected").register(registry);
  }

  @Override
  public void sent(int count) {
    sent.increment(count);
  }

  @Override
  public void dropped(int count) {
    dropped.increment(count);
  }

  @Override
  public void rejected(int count) {
    rejected.increment(count);
  }
}
