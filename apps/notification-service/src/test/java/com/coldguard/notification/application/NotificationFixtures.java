package com.coldguard.notification.application;

import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.notification.application.NotificationLedgerRepository.Delivery;
import com.coldguard.notification.application.NotificationLedgerRepository.Notification;
import com.coldguard.notification.application.NotificationSender.SendResult;
import com.coldguard.notification.domain.DeliveryStatus;
import com.coldguard.notification.domain.FailureCategory;
import com.coldguard.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory collaborators for use-case tests; no mail server, broker or database. */
final class NotificationFixtures {

  static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

  private NotificationFixtures() {}

  static NotificationRequest request(String... userIds) {
    List<NotificationRequest.Recipient> recipients = new ArrayList<>();
    for (String id : userIds) {
      recipients.add(new NotificationRequest.Recipient(id, id + "@coldguard.test"));
    }
    return new NotificationRequest(
        REQUEST_ID, INCIDENT_ID, "INCIDENT_CREATED", "P1", "asset-1", "sensor-1", recipients);
  }

  static final class InMemoryLedger implements NotificationLedgerRepository {
    final Map<UUID, NotificationStatus> status = new HashMap<>();
    final Map<String, DeliveryStatus> delivery = new LinkedHashMap<>();
    final Map<String, Integer> attempts = new HashMap<>();

    @Override
    public boolean insertIfAbsent(NotificationRequest request, Instant now) {
      if (status.containsKey(request.requestId())) {
        return false;
      }
      status.put(request.requestId(), NotificationStatus.PENDING);
      request.recipients().forEach(r -> delivery.put(r.userId(), DeliveryStatus.PENDING));
      return true;
    }

    @Override
    public Optional<Notification> find(UUID requestId) {
      return Optional.ofNullable(status.get(requestId))
          .map(s -> new Notification(UUID.randomUUID(), INCIDENT_ID, s));
    }

    @Override
    public List<Delivery> deliveries(UUID requestId) {
      return delivery.entrySet().stream()
          .map(e -> new Delivery(e.getKey(), e.getValue(), attempts.getOrDefault(e.getKey(), 0)))
          .toList();
    }

    @Override
    public void markSent(UUID requestId, String user, Instant now) {
      delivery.put(user, DeliveryStatus.SENT);
      attempts.merge(user, 1, Integer::sum);
    }

    @Override
    public void recordTransientAttempt(UUID requestId, String user, String category) {
      attempts.merge(user, 1, Integer::sum);
    }

    @Override
    public void markFailed(UUID requestId, String user, FailureCategory category) {
      delivery.put(user, DeliveryStatus.FAILED);
      if (category == FailureCategory.PERMANENT) {
        attempts.merge(user, 1, Integer::sum);
      }
    }

    @Override
    public void updateStatus(UUID requestId, NotificationStatus s, Instant now) {
      status.put(requestId, s);
    }
  }

  static final class CapturedEvents implements DomainEventPublisher {
    final List<OutboundEvent> published = new ArrayList<>();

    @Override
    public void publish(OutboundEvent event) {
      published.add(event);
    }
  }

  /** Answers per recipient user id (default: sent) and remembers who it was asked to reach. */
  static final class ScriptedSender implements NotificationSender {
    final Map<String, SendResult> answers = new HashMap<>();
    final List<String> attempted = new ArrayList<>();

    @Override
    public SendResult send(NotificationRequest.Recipient recipient, NotificationContent content) {
      attempted.add(recipient.userId());
      return answers.getOrDefault(recipient.userId(), new SendResult.Sent());
    }
  }

  static final NotificationTemplateRenderer RENDERER =
      request -> new NotificationContent("subject", "body");
}
