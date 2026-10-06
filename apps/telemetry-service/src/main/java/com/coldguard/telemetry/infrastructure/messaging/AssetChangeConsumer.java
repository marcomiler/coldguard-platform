package com.coldguard.telemetry.infrastructure.messaging;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.telemetry.application.AssetChangeHandler;
import com.coldguard.telemetry.domain.SensorStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Keeps Telemetry's view of sensors in step with Asset. The deduplication row commits together with
 * the effect, and the message is acknowledged only afterwards, so a redelivery changes nothing.
 * Events are applied as "set to this value" (never "toggle"), and an older change never overwrites
 * a newer one, so out-of-order delivery is harmless; the cache's time limit covers a late event.
 */
@Component
class AssetChangeConsumer {

  static final String CONSUMER = "telemetry-service.asset-changes";

  private static final Map<String, Set<Integer>> SUPPORTED =
      Map.of(
          "SensorStatusChanged", Set.of(1),
          "SensorRetired", Set.of(1),
          "SensorReassigned", Set.of(1),
          "OperationalProfileUpdated", Set.of(1),
          "AssetUpdated", Set.of(1));

  private final EnvelopeCodec codec;
  private final InboxGuard inbox;
  private final AssetChangeHandler handler;

  AssetChangeConsumer(EnvelopeCodec codec, InboxGuard inbox, AssetChangeHandler handler) {
    this.codec = codec;
    this.inbox = inbox;
    this.handler = handler;
  }

  @RabbitListener(queues = TelemetryMessagingTopology.ASSET_CHANGES_QUEUE)
  void on(Message message) {
    EventEnvelope event = codec.read(message.getBody(), SUPPORTED);
    JsonNode payload = event.payload();
    Instant at = event.occurredAt();
    if (at == null) {
      throw new PermanentMessageException("Event " + event.eventType() + " has no occurredAt");
    }
    inbox.runOnce(
        event.eventId(),
        CONSUMER,
        () -> {
          switch (event.eventType()) {
            case "SensorStatusChanged" ->
                handler.sensorStatusChanged(
                    uuid(payload, "sensorId"), status(payload, "newStatus"), at);
            case "SensorRetired" ->
                handler.sensorStatusChanged(uuid(payload, "sensorId"), SensorStatus.RETIRED, at);
            case "SensorReassigned" ->
                handler.sensorReassigned(
                    uuid(payload, "sensorId"), uuid(payload, "newAssetId"), at);
            case "OperationalProfileUpdated" ->
                handler.expectedIntervalChanged(
                    uuid(payload, "sensorId"), interval(payload.path("current")), at);
            case "AssetUpdated" -> handler.assetUpdated();
            default ->
                throw new PermanentMessageException("Unsupported event " + event.eventType());
          }
        });
  }

  private static UUID uuid(JsonNode payload, String field) {
    try {
      return UUID.fromString(payload.path(field).asString(""));
    } catch (IllegalArgumentException e) {
      throw new PermanentMessageException("Field " + field + " is not a valid id");
    }
  }

  private static SensorStatus status(JsonNode payload, String field) {
    try {
      return SensorStatus.valueOf(payload.path(field).asString(""));
    } catch (IllegalArgumentException e) {
      throw new PermanentMessageException("Field " + field + " is not a known sensor status");
    }
  }

  private static int interval(JsonNode profile) {
    int seconds = profile.path("expectedIntervalSeconds").asInt(0);
    if (seconds < 1) {
      throw new PermanentMessageException("expectedIntervalSeconds must be at least 1");
    }
    return seconds;
  }
}
