package com.coldguard.simulator.config;

import com.coldguard.simulator.application.SimulatorTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the tick on a fixed delay, so two ticks never overlap. */
@Component
@ConditionalOnProperty(
    name = "coldguard.simulator.enabled",
    havingValue = "true",
    matchIfMissing = true)
class SimulatorScheduler {

  private static final Logger log = LoggerFactory.getLogger(SimulatorScheduler.class);

  private final SimulatorTick tick;

  SimulatorScheduler(SimulatorTick tick) {
    this.tick = tick;
  }

  @Scheduled(fixedDelayString = "${coldguard.simulator.tick-interval:1s}")
  void run() {
    try {
      tick.run();
    } catch (RuntimeException e) {
      // A failed tick must not stop the simulator.
      log.error("Simulator tick failed: {}", e.getClass().getSimpleName());
    }
  }
}
