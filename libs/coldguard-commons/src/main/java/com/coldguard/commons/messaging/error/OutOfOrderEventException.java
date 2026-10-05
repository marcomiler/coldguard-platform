package com.coldguard.commons.messaging.error;

/**
 * An event arrived ahead of an earlier event of its aggregate. Transient: the delivery is retried
 * (and finally dead-lettered) so the missing event has time to arrive.
 */
public class OutOfOrderEventException extends RuntimeException {

  public OutOfOrderEventException(
      String aggregateType, String aggregateId, long expected, long got) {
    super(
        "Out-of-order event for %s %s: expected version %d but got %d"
            .formatted(aggregateType, aggregateId, expected, got));
  }
}
