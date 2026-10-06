package com.coldguard.telemetry.application;

/** What happened to one reading of a batch. {@code rejectionCode} is set only when REJECTED. */
public record ReadingResult(
    String readingId,
    ReadingOutcome outcome,
    boolean eligible,
    boolean breached,
    String rejectionCode) {

  static ReadingResult rejected(String readingId, String code) {
    return new ReadingResult(readingId, ReadingOutcome.REJECTED, false, false, code);
  }

  static ReadingResult duplicate(String readingId) {
    return new ReadingResult(readingId, ReadingOutcome.DUPLICATE, false, false, null);
  }
}
