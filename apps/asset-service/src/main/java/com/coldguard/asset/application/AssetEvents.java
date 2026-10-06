package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the outbound events of the Asset context; payloads follow contracts/events/asset. */
final class AssetEvents {

  private static final String ASSET = "Asset";
  private static final String SENSOR = "Sensor";

  private AssetEvents() {}

  record AssetRegistered(UUID assetId, UUID siteId, String name, String criticality) {}

  record AssetUpdated(
      UUID assetId,
      List<String> changedFields,
      Map<String, Object> previous,
      Map<String, Object> current) {}

  record OperationalProfileUpdated(
      UUID sensorId,
      long profileVersion,
      Map<String, Object> previous,
      Map<String, Object> current) {}

  record SensorCalibrationRecorded(
      UUID sensorId,
      UUID calibrationId,
      String kind,
      Instant performedAt,
      Instant validUntil,
      String reason) {}

  static OutboundEvent assetRegistered(Asset asset, EventActor actor, Instant now) {
    return new OutboundEvent(
        "AssetRegistered",
        1,
        ASSET,
        asset.id().toString(),
        "asset.asset-registered",
        actor,
        now,
        new AssetRegistered(asset.id(), asset.siteId(), asset.name(), asset.criticality().name()));
  }

  static OutboundEvent assetUpdated(
      Asset before, Asset after, List<String> changedFields, EventActor actor, Instant now) {
    return new OutboundEvent(
        "AssetUpdated",
        1,
        ASSET,
        after.id().toString(),
        "asset.asset-updated",
        actor,
        now,
        new AssetUpdated(
            after.id(),
            changedFields,
            values(before, changedFields),
            values(after, changedFields)));
  }

  static OutboundEvent operationalProfileUpdated(
      OperationalProfile previous, OperationalProfile current, EventActor actor, Instant now) {
    return new OutboundEvent(
        "OperationalProfileUpdated",
        1,
        SENSOR,
        current.sensorId().toString(),
        "asset.operational-profile-updated",
        actor,
        now,
        new OperationalProfileUpdated(
            current.sensorId(),
            current.version(),
            previous == null ? null : previous.snapshot(),
            current.snapshot()));
  }

  static OutboundEvent sensorCalibrationRecorded(
      CalibrationRecord record, EventActor actor, Instant now) {
    return new OutboundEvent(
        "SensorCalibrationRecorded",
        1,
        SENSOR,
        record.sensorId().toString(),
        "asset.sensor-calibration-recorded",
        actor,
        now,
        new SensorCalibrationRecorded(
            record.sensorId(),
            record.id(),
            record.kind().name(),
            record.performedAt(),
            record.validUntil(),
            record.reason()));
  }

  private static Map<String, Object> values(Asset asset, List<String> fields) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (String field : fields) {
      values.put(
          field,
          switch (field) {
            case "name" -> asset.name();
            case "description" -> asset.description();
            case "criticality" -> asset.criticality().name();
            default -> throw new IllegalStateException("Unknown asset field " + field);
          });
    }
    return values;
  }
}
