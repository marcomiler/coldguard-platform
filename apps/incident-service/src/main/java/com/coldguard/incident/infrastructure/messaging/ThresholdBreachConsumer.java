package com.coldguard.incident.infrastructure.messaging;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.incident.application.HandleThresholdBreachService;
import com.coldguard.incident.application.OpenIncidentCommand;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Magnitude;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Opens or updates an incident from a threshold breach. The deduplication row commits together with
 * the incident change and the message is acknowledged only afterwards, so a redelivery does not
 * count the same occurrence twice. A payload that can never be understood is rejected to the
 * dead-letter queue instead of retried.
 */
@Component
class ThresholdBreachConsumer {

  static final String CONSUMER = "incident-service.telemetry-threshold-breached";

  private static final Map<String, Set<Integer>> SUPPORTED =
      Map.of("TelemetryThresholdBreached", Set.of(1));

  private final EnvelopeCodec codec;
  private final InboxGuard inbox;
  private final HandleThresholdBreachService handler;

  ThresholdBreachConsumer(
      EnvelopeCodec codec, InboxGuard inbox, HandleThresholdBreachService handler) {
    this.codec = codec;
    this.inbox = inbox;
    this.handler = handler;
  }

  @RabbitListener(queues = IncidentMessagingTopology.THRESHOLD_BREACHED_QUEUE)
  void on(Message message) {
    EventEnvelope event = codec.read(message.getBody(), SUPPORTED);
    OpenIncidentCommand command = toCommand(event.payload());
    inbox.runOnce(event.eventId(), CONSUMER, () -> handler.handle(command));
  }

  private static OpenIncidentCommand toCommand(JsonNode payload) {
    return new OpenIncidentCommand(
        id(payload, "assetId"),
        enumValue(Criticality.class, payload, "assetCriticality"),
        id(payload, "sensorId"),
        text(payload, "anomalyType"),
        enumValue(Magnitude.class, payload, "magnitude"),
        payload.path("persistent").asBoolean(false),
        id(payload, "readingId"));
  }

  private static String text(JsonNode payload, String field) {
    String value = payload.path(field).asString("");
    if (value.isBlank()) {
      throw new PermanentMessageException("Field " + field + " is required");
    }
    return value;
  }

  private static String id(JsonNode payload, String field) {
    String value = text(payload, field);
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException e) {
      throw new PermanentMessageException("Field " + field + " is not a valid id");
    }
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode payload, String field) {
    try {
      return Enum.valueOf(type, text(payload, field));
    } catch (IllegalArgumentException e) {
      throw new PermanentMessageException("Field " + field + " has an unknown value");
    }
  }
}
