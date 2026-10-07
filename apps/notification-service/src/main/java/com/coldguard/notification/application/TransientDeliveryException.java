package com.coldguard.notification.application;

import java.util.UUID;

/** At least one recipient could not be reached for now; the broker redelivers the request. */
public class TransientDeliveryException extends RuntimeException {

  public TransientDeliveryException(UUID requestId, int failed) {
    super(
        "Delivery of notification %s failed transiently for %d recipient(s)"
            .formatted(requestId, failed));
  }
}
