package com.coldguard.gateway.api.telemetry;

import java.util.List;

/** One result per reading, in the order they were sent. */
public record TestReadingsResponse(List<ReadingResult> results) {}
