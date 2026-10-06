package com.coldguard.telemetry.infrastructure;

import com.coldguard.telemetry.application.ConnectivityMetrics;
import com.coldguard.telemetry.application.IngestMetrics;
import com.coldguard.telemetry.application.ReadingOutcome;
import com.coldguard.telemetry.domain.ReadingSource;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * {@code coldguard.telemetry.readings{source,outcome,eligible,breached}}: readings per minute, and
 * {@code coldguard.telemetry.connectivity.lost}: sensors found silent.
 */
public class MicrometerIngestMetrics implements IngestMetrics, ConnectivityMetrics {

  private final MeterRegistry meters;

  public MicrometerIngestMetrics(MeterRegistry meters) {
    this.meters = meters;
  }

  @Override
  public void record(
      ReadingSource source, ReadingOutcome outcome, boolean eligible, boolean breached) {
    meters
        .counter(
            "coldguard.telemetry.readings",
            "source",
            source.name(),
            "outcome",
            outcome.name(),
            "eligible",
            String.valueOf(eligible),
            "breached",
            String.valueOf(breached))
        .increment();
  }

  @Override
  public void connectivityLost() {
    meters.counter("coldguard.telemetry.connectivity.lost").increment();
  }
}
