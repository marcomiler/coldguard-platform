package com.coldguard.telemetry.domain;

/**
 * Where a reading came from. It is evidence only: it never changes how the reading is evaluated.
 */
public enum ReadingSource {
  SIMULATOR,
  TEST_INJECTION
}
