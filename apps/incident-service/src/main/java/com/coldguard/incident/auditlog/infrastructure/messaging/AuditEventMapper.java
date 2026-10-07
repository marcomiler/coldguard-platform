package com.coldguard.incident.auditlog.infrastructure.messaging;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.incident.auditlog.application.AuditEntry;
import com.coldguard.incident.auditlog.application.AuditEntry.ActorType;
import com.coldguard.incident.auditlog.application.AuditJson;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Translates each audited event into an audit entry: who did it (the envelope's actor), when, why
 * (the payload's reason) and the values before and after. An event type without an explicit mapping
 * is rejected, never guessed: it dead-letters so the gap is noticed.
 */
@Component
class AuditEventMapper {

  /** Event types this mapper understands, with the versions it supports. */
  static final Map<String, Set<Integer>> SUPPORTED =
      Map.of(
          "AssetRegistered", Set.of(1),
          "AssetUpdated", Set.of(1),
          "OperationalProfileUpdated", Set.of(1),
          "SensorStatusChanged", Set.of(1),
          "SensorReassigned", Set.of(1),
          "SensorCalibrationRecorded", Set.of(1),
          "SensorCalibrationExpired", Set.of(1),
          "SensorRetired", Set.of(1),
          "SensorConnectivityLost", Set.of(1),
          "NotificationFailed", Set.of(1));

  AuditEntry map(EventEnvelope event) {
    JsonNode p = event.payload();
    String reason = text(p, "reason");
    String before = null;
    String after = null;
    String action;
    switch (event.eventType()) {
      case "AssetRegistered" -> {
        action = "ASSET_REGISTERED";
        after =
            AuditJson.object(
                "siteId", text(p, "siteId"),
                "name", text(p, "name"),
                "criticality", text(p, "criticality"));
      }
      case "AssetUpdated" -> {
        action = "ASSET_UPDATED";
        before = object(p, "previous");
        after = object(p, "current");
      }
      case "OperationalProfileUpdated" -> {
        action = "OPERATIONAL_PROFILE_UPDATED";
        before = object(p, "previous");
        after = object(p, "current");
      }
      case "SensorStatusChanged" -> {
        action = "SENSOR_STATUS_CHANGED";
        before = AuditJson.object("status", text(p, "previousStatus"));
        after = AuditJson.object("status", text(p, "newStatus"));
      }
      case "SensorReassigned" -> {
        action = "SENSOR_REASSIGNED";
        before = AuditJson.object("assetId", text(p, "previousAssetId"));
        after = AuditJson.object("assetId", text(p, "newAssetId"));
      }
      case "SensorCalibrationRecorded" -> {
        action = "SENSOR_CALIBRATION_RECORDED";
        after =
            AuditJson.object(
                "calibrationId", text(p, "calibrationId"),
                "kind", text(p, "kind"),
                "performedAt", text(p, "performedAt"),
                "validUntil", text(p, "validUntil"));
      }
      case "SensorCalibrationExpired" -> {
        action = "SENSOR_CALIBRATION_EXPIRED";
        after =
            AuditJson.object(
                "calibrationId", text(p, "calibrationId"),
                "expiredAt", text(p, "expiredAt"),
                "detectedAt", text(p, "detectedAt"));
      }
      case "SensorRetired" -> {
        action = "SENSOR_RETIRED";
        before = AuditJson.object("status", text(p, "previousStatus"));
        after = AuditJson.object("status", "RETIRED");
      }
      case "SensorConnectivityLost" -> {
        action = "SENSOR_CONNECTIVITY_LOST";
        after =
            AuditJson.object(
                "lastReadingAt", text(p, "lastReadingAt"),
                "expectedIntervalSeconds", text(p, "expectedIntervalSeconds"),
                "detectedAt", text(p, "detectedAt"));
      }
      case "NotificationFailed" -> {
        action = "NOTIFICATION_FAILED";
        after =
            AuditJson.object(
                "notificationRequestId", text(p, "notificationRequestId"),
                "failureCategory", text(p, "failureCategory"),
                "attempts", text(p, "attempts"));
      }
      default -> throw new PermanentMessageException("No audit mapping for " + event.eventType());
    }
    EventActor actor = event.actor();
    if (actor == null || actor.id() == null || event.occurredAt() == null) {
      throw new PermanentMessageException(event.eventType() + " lacks actor or occurredAt");
    }
    return new AuditEntry(
        event.producer(),
        event.aggregateType(),
        event.aggregateId(),
        action,
        actor.type() == EventActor.Type.SYSTEM ? ActorType.SYSTEM : ActorType.USER,
        actor.id(),
        reason,
        before,
        after,
        event.occurredAt(),
        event.correlationId(),
        event.eventId());
  }

  private static String text(JsonNode payload, String field) {
    JsonNode node = payload.path(field);
    return node.isMissingNode() || node.isNull() ? null : node.asString();
  }

  private static String object(JsonNode payload, String field) {
    JsonNode node = payload.path(field);
    return node.isObject() ? node.toString() : null;
  }
}
