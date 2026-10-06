package com.coldguard.gateway.api.telemetry;

/** What happened to one reading; {@code rejectionCode} is set only when it was rejected. */
public record ReadingResult(
    String readingId,
    ReadingOutcome outcome,
    boolean eligible,
    boolean breached,
    String rejectionCode) {}
