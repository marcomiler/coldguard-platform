package com.coldguard.notification.domain;

import java.util.Collection;

/** Overall state of one notification request. */
public enum NotificationStatus {
  PENDING,
  SENT,
  PARTIALLY_SENT,
  FAILED;

  /** Derives the overall state from the deliveries of the request. */
  public static NotificationStatus of(Collection<DeliveryStatus> deliveries) {
    boolean pending = deliveries.contains(DeliveryStatus.PENDING);
    boolean sent = deliveries.contains(DeliveryStatus.SENT);
    boolean failed = deliveries.contains(DeliveryStatus.FAILED);
    if (pending) {
      return PENDING;
    }
    if (failed) {
      return sent ? PARTIALLY_SENT : FAILED;
    }
    return SENT;
  }

  /** No further delivery will be attempted. */
  public boolean isFinal() {
    return this != PENDING;
  }
}
