package com.coldguard.simulator.application;

import com.coldguard.simulator.domain.PendingReading;
import java.util.List;

/** Port to whatever receives the readings (Telemetry Service over gRPC). */
public interface ReadingSender {

  /** What happened to the batch. */
  sealed interface Outcome {

    /** Delivered; {@code rejections} lists the readings Telemetry refused, never to be retried. */
    record Delivered(int accepted, int duplicates, List<Rejection> rejections) implements Outcome {}

    /** Telemetry could not be reached in time: keep the readings and try again. */
    record Unavailable(String reason) implements Outcome {}

    /** Refused as a whole for a reason a retry cannot fix. */
    record Refused(String reason) implements Outcome {}
  }

  /** A reading Telemetry did not accept, identified by sensor and code, never by value. */
  record Rejection(String sensorId, String code) {}

  Outcome send(List<PendingReading> batch);
}
