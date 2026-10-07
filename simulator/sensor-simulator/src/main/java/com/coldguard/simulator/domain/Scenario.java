package com.coldguard.simulator.domain;

/** The five behaviours the demo needs (CU-002). */
public enum Scenario {
  /** Values within the profile's range. */
  NOMINAL,
  /** One out-of-range reading, then nominal again. */
  OUT_OF_RANGE,
  /** A few out-of-range readings, then nominal: the streak restarts before it persists. */
  RECOVERY,
  /** Out-of-range values sustained for as long as the simulator runs. */
  PERSISTENCE,
  /** The sensor goes silent for a while; Telemetry detects the absence. */
  CONNECTIVITY_LOSS
}
