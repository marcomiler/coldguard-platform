package com.coldguard.notification.infrastructure.messaging;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.notification.application.HandleNotificationRequestedService;
import com.coldguard.notification.application.NotificationRequest;
import com.coldguard.notification.application.NotificationRequest.Recipient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Turns a {@code NotificationRequested} message into a request and hands it over. A message that
 * can never be processed (malformed, unknown type, no recipients) goes to the dead-letter queue; a
 * transient delivery failure is thrown so the broker redelivers.
 */
@Component
class NotificationRequestedConsumer {

  private static final Map<String, Set<Integer>> SUPPORTED =
      Map.of("NotificationRequested", Set.of(1));
  private static final Set<String> TYPES = Set.of("INCIDENT_CREATED", "INCIDENT_ESCALATED");

  private final EnvelopeCodec codec;
  private final HandleNotificationRequestedService handler;

  NotificationRequestedConsumer(EnvelopeCodec codec, HandleNotificationRequestedService handler) {
    this.codec = codec;
    this.handler = handler;
  }

  @RabbitListener(queues = NotificationMessagingTopology.NOTIFICATION_REQUESTED_QUEUE)
  void on(Message message) {
    handler.handle(toRequest(codec.read(message.getBody(), SUPPORTED)));
  }

  static NotificationRequest toRequest(EventEnvelope event) {
    JsonNode p = event.payload();
    String type = text(p, "notificationType");
    if (!TYPES.contains(type)) {
      throw new PermanentMessageException("Unsupported notification type");
    }
    List<Recipient> recipients = new ArrayList<>();
    for (JsonNode r : p.path("recipients")) {
      recipients.add(new Recipient(text(r, "userId"), text(r, "email")));
    }
    if (recipients.isEmpty()) {
      throw new PermanentMessageException("Notification has no recipients");
    }
    return new NotificationRequest(
        uuid(p, "notificationRequestId"),
        uuid(p, "incidentId"),
        type,
        p.path("priority").asString(null),
        text(p, "assetId"),
        text(p, "sensorId"),
        List.copyOf(recipients));
  }

  private static String text(JsonNode node, String field) {
    String value = node.path(field).asString("");
    if (value.isBlank()) {
      throw new PermanentMessageException("Field " + field + " is required");
    }
    return value;
  }

  private static UUID uuid(JsonNode node, String field) {
    try {
      return UUID.fromString(text(node, field));
    } catch (IllegalArgumentException e) {
      throw new PermanentMessageException("Field " + field + " is not a valid id");
    }
  }
}
