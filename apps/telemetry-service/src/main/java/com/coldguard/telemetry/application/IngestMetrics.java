package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.ReadingSource;

/** Counts what ingestion did, by source and result (the readings-per-minute indicator). */
public interface IngestMetrics {

  void record(ReadingSource source, ReadingOutcome outcome, boolean eligible, boolean breached);
}
