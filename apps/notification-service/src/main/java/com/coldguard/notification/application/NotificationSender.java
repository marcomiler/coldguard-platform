package com.coldguard.notification.application;

/**
 * Port to whatever delivers a message (SMTP today, a cloud email service later). Implementations
 * never throw for a delivery problem: they report it as a {@link SendResult}.
 */
public interface NotificationSender {

  sealed interface SendResult {

    record Sent() implements SendResult {}

    /** Worth retrying: connection, timeout, temporary refusal. */
    record TransientFailure(String reason) implements SendResult {}

    /** Retrying cannot help: the address was rejected. */
    record PermanentFailure(String reason) implements SendResult {}
  }

  SendResult send(NotificationRequest.Recipient recipient, NotificationContent content);
}
