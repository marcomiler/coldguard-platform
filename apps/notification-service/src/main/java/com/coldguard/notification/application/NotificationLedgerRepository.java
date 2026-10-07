package com.coldguard.notification.application;

import com.coldguard.notification.domain.DeliveryStatus;
import com.coldguard.notification.domain.FailureCategory;
import com.coldguard.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable record of what was asked and what was delivered. */
public interface NotificationLedgerRepository {

  record Delivery(String recipientUserId, DeliveryStatus status, int attempts) {}

  record Notification(UUID id, UUID incidentId, NotificationStatus status) {}

  /**
   * Records the request and a pending delivery per recipient.
   *
   * @return false when a request with this id already exists
   */
  boolean insertIfAbsent(NotificationRequest request, Instant now);

  Optional<Notification> find(UUID requestId);

  List<Delivery> deliveries(UUID requestId);

  void markSent(UUID requestId, String recipientUserId, Instant now);

  /** Counts a failed try that will be retried; the delivery stays pending. */
  void recordTransientAttempt(UUID requestId, String recipientUserId, String category);

  /** Gives up on one delivery; a permanent failure also counts as a try. */
  void markFailed(UUID requestId, String recipientUserId, FailureCategory category);

  void updateStatus(UUID requestId, NotificationStatus status, Instant now);
}
