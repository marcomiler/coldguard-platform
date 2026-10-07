package com.coldguard.notification.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.notification.application.NotificationLedgerRepository.Delivery;
import com.coldguard.notification.application.NotificationLedgerRepository.Notification;
import com.coldguard.notification.domain.DeliveryStatus;
import com.coldguard.notification.domain.FailureCategory;
import com.coldguard.notification.domain.NotificationStatus;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database transactions around sending. Sending itself is never inside one: the mail
 * server is not transactional, so each outcome is written as soon as it is known.
 */
@Service
public class NotificationLedger {

  private static final EventActor ACTOR = EventActor.system("notification-service");

  record NotificationFailedPayload(
      UUID notificationRequestId, UUID incidentId, String failureCategory, int attempts) {}

  private final NotificationLedgerRepository repository;
  private final DomainEventPublisher events;
  private final Clock clock;

  NotificationLedger(
      NotificationLedgerRepository repository, DomainEventPublisher events, Clock clock) {
    this.repository = repository;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Records the request unless it is already known.
   *
   * @return the recipients still to be notified: all of them for a new request, only the pending
   *     ones for a redelivery, none when the request already finished
   */
  @Transactional
  public Set<String> register(NotificationRequest request) {
    if (repository.insertIfAbsent(request, clock.instant())) {
      return request.recipients().stream()
          .map(NotificationRequest.Recipient::userId)
          .collect(Collectors.toSet());
    }
    Notification existing =
        repository.find(request.requestId()).orElseThrow(IllegalStateException::new);
    if (existing.status().isFinal()) {
      return Set.of();
    }
    return repository.deliveries(request.requestId()).stream()
        .filter(d -> d.status() == DeliveryStatus.PENDING)
        .map(Delivery::recipientUserId)
        .collect(Collectors.toSet());
  }

  @Transactional
  public void recordSent(UUID requestId, String recipientUserId) {
    repository.markSent(requestId, recipientUserId, clock.instant());
    refresh(requestId);
  }

  @Transactional
  public void recordTransientFailure(UUID requestId, String recipientUserId) {
    repository.recordTransientAttempt(requestId, recipientUserId, "TRANSIENT");
  }

  @Transactional
  public void recordPermanentFailure(UUID requestId, String recipientUserId) {
    repository.markFailed(requestId, recipientUserId, FailureCategory.PERMANENT);
    int attempts =
        repository.deliveries(requestId).stream()
            .filter(d -> d.recipientUserId().equals(recipientUserId))
            .mapToInt(Delivery::attempts)
            .max()
            .orElse(1);
    publishFailure(requestId, FailureCategory.PERMANENT, attempts);
    refresh(requestId);
  }

  /** The broker gave up redelivering: whoever is still pending will not be notified. */
  @Transactional
  public void recordRetriesExhausted(UUID requestId) {
    List<Delivery> pending =
        repository.deliveries(requestId).stream()
            .filter(d -> d.status() == DeliveryStatus.PENDING)
            .toList();
    if (pending.isEmpty()) {
      return;
    }
    pending.forEach(
        d ->
            repository.markFailed(
                requestId, d.recipientUserId(), FailureCategory.RETRIES_EXHAUSTED));
    int attempts = pending.stream().mapToInt(Delivery::attempts).max().orElse(1);
    publishFailure(requestId, FailureCategory.RETRIES_EXHAUSTED, attempts);
    refresh(requestId);
  }

  private void refresh(UUID requestId) {
    NotificationStatus status =
        NotificationStatus.of(
            repository.deliveries(requestId).stream().map(Delivery::status).toList());
    repository.updateStatus(requestId, status, clock.instant());
  }

  private void publishFailure(UUID requestId, FailureCategory category, int attempts) {
    Notification notification = repository.find(requestId).orElseThrow(IllegalStateException::new);
    events.publish(
        new OutboundEvent(
            "NotificationFailed",
            1,
            "Incident",
            notification.incidentId().toString(),
            "notification.notification-failed",
            ACTOR,
            clock.instant(),
            new NotificationFailedPayload(
                requestId, notification.incidentId(), category.name(), Math.max(1, attempts))));
  }
}
