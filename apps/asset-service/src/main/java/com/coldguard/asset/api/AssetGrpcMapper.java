package com.coldguard.asset.api;

import com.coldguard.asset.application.CursorPage;
import com.coldguard.asset.application.OperationalProfileDraft;
import com.coldguard.asset.application.Page;
import com.coldguard.asset.application.SensorEvaluationContext;
import com.coldguard.asset.application.SensorHistoryEntry;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.Site;
import com.coldguard.common.grpc.v1.PageInfo;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Translates between the gRPC messages and the domain model; no business rule lives here. */
final class AssetGrpcMapper {

  private AssetGrpcMapper() {}

  // ---- identifiers --------------------------------------------------------------------------

  /** An id that is not a UUID cannot exist, so it is reported as not found. */
  static UUID id(String kind, String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException notAnId) {
      throw new ResourceNotFoundException(kind, value);
    }
  }

  /** Empty means "no filter". */
  static UUID optionalId(String kind, String value) {
    return value == null || value.isEmpty() ? null : id(kind, value);
  }

  // ---- to gRPC ------------------------------------------------------------------------------

  static com.coldguard.asset.grpc.v1.Organization toGrpc(Organization organization) {
    return com.coldguard.asset.grpc.v1.Organization.newBuilder()
        .setId(organization.id().toString())
        .setName(organization.name())
        .setCreatedAt(timestamp(organization.createdAt()))
        .build();
  }

  static com.coldguard.asset.grpc.v1.Site toGrpc(Site site) {
    com.coldguard.asset.grpc.v1.Site.Builder builder =
        com.coldguard.asset.grpc.v1.Site.newBuilder()
            .setId(site.id().toString())
            .setOrganizationId(site.organizationId().toString())
            .setName(site.name())
            .setCreatedAt(timestamp(site.createdAt()));
    if (site.address() != null) {
      builder.setAddress(site.address());
    }
    return builder.build();
  }

  static com.coldguard.asset.grpc.v1.Asset toGrpc(Asset asset) {
    com.coldguard.asset.grpc.v1.Asset.Builder builder =
        com.coldguard.asset.grpc.v1.Asset.newBuilder()
            .setId(asset.id().toString())
            .setSiteId(asset.siteId().toString())
            .setName(asset.name())
            .setCriticality(toGrpc(asset.criticality()))
            .setCreatedAt(timestamp(asset.createdAt()))
            .setUpdatedAt(timestamp(asset.updatedAt()))
            .setVersion(asset.version());
    if (asset.description() != null) {
      builder.setDescription(asset.description());
    }
    return builder.build();
  }

  static com.coldguard.asset.grpc.v1.Sensor toGrpc(Sensor sensor) {
    com.coldguard.asset.grpc.v1.Sensor.Builder builder =
        com.coldguard.asset.grpc.v1.Sensor.newBuilder()
            .setId(sensor.id().toString())
            .setSerialNumber(sensor.serialNumber())
            .setMeasurementUnit(sensor.measurementUnit())
            .setAssetId(sensor.assetId().toString())
            .setStatus(toGrpc(sensor.status()))
            .setStatusChangedAt(timestamp(sensor.statusChangedAt()))
            .setCreatedAt(timestamp(sensor.createdAt()))
            .setUpdatedAt(timestamp(sensor.updatedAt()))
            .setVersion(sensor.version());
    if (sensor.model() != null) {
      builder.setModel(sensor.model());
    }
    if (sensor.lastCalibrationRecordedAt() != null) {
      builder.setLastCalibrationRecordedAt(timestamp(sensor.lastCalibrationRecordedAt()));
    }
    if (sensor.lastCalibrationValidUntil() != null) {
      builder.setLastCalibrationValidUntil(timestamp(sensor.lastCalibrationValidUntil()));
    }
    return builder.build();
  }

  static com.coldguard.asset.grpc.v1.OperationalProfile toGrpc(OperationalProfile profile) {
    com.coldguard.asset.grpc.v1.OperationalProfile.Builder builder =
        com.coldguard.asset.grpc.v1.OperationalProfile.newBuilder()
            .setSensorId(profile.sensorId().toString())
            .setMinTemperature(profile.minTemperature().doubleValue())
            .setMaxTemperature(profile.maxTemperature().doubleValue())
            .setUnit(profile.unit())
            .setMagnitudeBands(
                com.coldguard.asset.grpc.v1.MagnitudeBands.newBuilder()
                    .setMediumFrom(profile.magnitudeMediumFrom().doubleValue())
                    .setHighFrom(profile.magnitudeHighFrom().doubleValue())
                    .setCriticalFrom(profile.magnitudeCriticalFrom().doubleValue()))
            .setPersistence(
                com.coldguard.asset.grpc.v1.PersistenceWindow.newBuilder()
                    .setMinConsecutiveBreaches(profile.persistenceMinConsecutive())
                    .setWindow(duration(profile.persistenceWindow())))
            .setExpectedReadingInterval(duration(profile.expectedInterval()))
            .setVersion(profile.version())
            .setUpdatedAt(timestamp(profile.updatedAt()));
    if (profile.calibrationValidity() != null) {
      builder.setCalibrationValidity(duration(profile.calibrationValidity()));
    }
    return builder.build();
  }

  static com.coldguard.asset.grpc.v1.CalibrationRecord toGrpc(CalibrationRecord record) {
    return com.coldguard.asset.grpc.v1.CalibrationRecord.newBuilder()
        .setId(record.id().toString())
        .setSensorId(record.sensorId().toString())
        .setKind(toGrpc(record.kind()))
        .setPerformedAt(timestamp(record.performedAt()))
        .setValidUntil(timestamp(record.validUntil()))
        .setRecordedAt(timestamp(record.recordedAt()))
        .setRecordedBy(record.recordedBy())
        .setReason(record.reason())
        .build();
  }

  static com.coldguard.asset.grpc.v1.SensorHistoryEntry toGrpc(SensorHistoryEntry entry) {
    com.coldguard.asset.grpc.v1.SensorHistoryEntry.Builder builder =
        com.coldguard.asset.grpc.v1.SensorHistoryEntry.newBuilder()
            .setId(entry.id().toString())
            .setAction(entry.action())
            .setActorType(entry.actorType())
            .setActorId(entry.actorId())
            .setOccurredAt(timestamp(entry.occurredAt()));
    if (entry.previousValue() != null) {
      builder.setPreviousValue(struct(entry.previousValue()));
    }
    if (entry.newValue() != null) {
      builder.setNewValue(struct(entry.newValue()));
    }
    if (entry.reason() != null) {
      builder.setReason(entry.reason());
    }
    return builder.build();
  }

  static com.coldguard.asset.grpc.v1.SensorEvaluationContext toGrpc(
      SensorEvaluationContext context) {
    com.coldguard.asset.grpc.v1.SensorEvaluationContext.Builder builder =
        com.coldguard.asset.grpc.v1.SensorEvaluationContext.newBuilder()
            .setSensorId(context.sensorId().toString())
            .setAssetId(context.assetId().toString())
            .setAssetCriticality(toGrpc(context.assetCriticality()))
            .setStatus(toGrpc(context.status()));
    if (context.profile() != null) {
      builder.setProfile(toGrpc(context.profile()));
    }
    return builder.build();
  }

  static com.coldguard.common.grpc.v1.CursorPageInfo cursorInfo(CursorPage<?> page) {
    return com.coldguard.common.grpc.v1.CursorPageInfo.newBuilder()
        .setNextCursor(page.nextCursor())
        .setHasMore(page.hasMore())
        .build();
  }

  static PageInfo pageInfo(Page<?> page) {
    return PageInfo.newBuilder()
        .setPage(page.page())
        .setSize(page.size())
        .setTotalElements(page.totalElements())
        .setTotalPages(page.totalPages())
        .build();
  }

  // ---- from gRPC ----------------------------------------------------------------------------

  static OperationalProfileDraft toDraft(
      UUID sensorId, com.coldguard.asset.grpc.v1.OperationalProfile profile) {
    var bands = profile.getMagnitudeBands();
    var persistence = profile.getPersistence();
    return new OperationalProfileDraft(
        sensorId,
        decimal(profile.getMinTemperature()),
        decimal(profile.getMaxTemperature()),
        profile.getUnit(),
        decimal(bands.getMediumFrom()),
        decimal(bands.getHighFrom()),
        decimal(bands.getCriticalFrom()),
        persistence.getMinConsecutiveBreaches(),
        toDuration(persistence.getWindow()),
        toDuration(profile.getExpectedReadingInterval()),
        profile.hasCalibrationValidity() ? toDuration(profile.getCalibrationValidity()) : null);
  }

  static Criticality toDomain(com.coldguard.common.grpc.v1.Criticality criticality) {
    return switch (criticality) {
      case CRITICALITY_LOW -> Criticality.LOW;
      case CRITICALITY_MEDIUM -> Criticality.MEDIUM;
      case CRITICALITY_HIGH -> Criticality.HIGH;
      case CRITICALITY_CRITICAL -> Criticality.CRITICAL;
      case CRITICALITY_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("criticality is required");
    };
  }

  static SensorStatus toDomain(com.coldguard.asset.grpc.v1.SensorStatus status) {
    return switch (status) {
      case SENSOR_STATUS_ACTIVE -> SensorStatus.ACTIVE;
      case SENSOR_STATUS_IN_MAINTENANCE -> SensorStatus.IN_MAINTENANCE;
      case SENSOR_STATUS_INACTIVE -> SensorStatus.INACTIVE;
      case SENSOR_STATUS_RETIRED -> SensorStatus.RETIRED;
      case SENSOR_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("status is required");
    };
  }

  static CalibrationKind toDomain(com.coldguard.asset.grpc.v1.CalibrationKind kind) {
    return switch (kind) {
      case CALIBRATION_KIND_CALIBRATION -> CalibrationKind.CALIBRATION;
      case CALIBRATION_KIND_VERIFICATION -> CalibrationKind.VERIFICATION;
      case CALIBRATION_KIND_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("calibration kind is required");
    };
  }

  static Instant toInstant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  /** Ids that are not UUIDs cannot match anything, so they are left out. */
  static List<UUID> idsIgnoringMalformed(List<String> values) {
    List<UUID> ids = new ArrayList<>();
    for (String value : values) {
      try {
        ids.add(UUID.fromString(value));
      } catch (IllegalArgumentException notAnId) {
        // skipped
      }
    }
    return ids;
  }

  // ---- primitives ---------------------------------------------------------------------------

  private static com.coldguard.common.grpc.v1.Criticality toGrpc(Criticality criticality) {
    return switch (criticality) {
      case LOW -> com.coldguard.common.grpc.v1.Criticality.CRITICALITY_LOW;
      case MEDIUM -> com.coldguard.common.grpc.v1.Criticality.CRITICALITY_MEDIUM;
      case HIGH -> com.coldguard.common.grpc.v1.Criticality.CRITICALITY_HIGH;
      case CRITICAL -> com.coldguard.common.grpc.v1.Criticality.CRITICALITY_CRITICAL;
    };
  }

  static com.coldguard.asset.grpc.v1.SensorStatus toGrpc(SensorStatus status) {
    return switch (status) {
      case ACTIVE -> com.coldguard.asset.grpc.v1.SensorStatus.SENSOR_STATUS_ACTIVE;
      case IN_MAINTENANCE -> com.coldguard.asset.grpc.v1.SensorStatus.SENSOR_STATUS_IN_MAINTENANCE;
      case INACTIVE -> com.coldguard.asset.grpc.v1.SensorStatus.SENSOR_STATUS_INACTIVE;
      case RETIRED -> com.coldguard.asset.grpc.v1.SensorStatus.SENSOR_STATUS_RETIRED;
    };
  }

  private static BigDecimal decimal(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException("profile values must be finite numbers");
    }
    return BigDecimal.valueOf(value);
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Duration duration(java.time.Duration duration) {
    return Duration.newBuilder()
        .setSeconds(duration.getSeconds())
        .setNanos(duration.getNano())
        .build();
  }

  private static java.time.Duration toDuration(Duration duration) {
    return java.time.Duration.ofSeconds(duration.getSeconds(), duration.getNanos());
  }

  private static com.coldguard.asset.grpc.v1.CalibrationKind toGrpc(CalibrationKind kind) {
    return switch (kind) {
      case CALIBRATION -> com.coldguard.asset.grpc.v1.CalibrationKind.CALIBRATION_KIND_CALIBRATION;
      case VERIFICATION ->
          com.coldguard.asset.grpc.v1.CalibrationKind.CALIBRATION_KIND_VERIFICATION;
    };
  }

  /** The JSON-like values of a history entry as a protobuf Struct. */
  private static Struct struct(Map<String, Object> values) {
    Struct.Builder builder = Struct.newBuilder();
    values.forEach((key, value) -> builder.putFields(key, value(value)));
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private static Value value(Object value) {
    return switch (value) {
      case null -> Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
      case Boolean flag -> Value.newBuilder().setBoolValue(flag).build();
      case Number number -> Value.newBuilder().setNumberValue(number.doubleValue()).build();
      case Map<?, ?> map ->
          Value.newBuilder().setStructValue(struct((Map<String, Object>) map)).build();
      case Iterable<?> items -> {
        ListValue.Builder list = ListValue.newBuilder();
        items.forEach(item -> list.addValues(value(item)));
        yield Value.newBuilder().setListValue(list).build();
      }
      default -> Value.newBuilder().setStringValue(value.toString()).build();
    };
  }
}
