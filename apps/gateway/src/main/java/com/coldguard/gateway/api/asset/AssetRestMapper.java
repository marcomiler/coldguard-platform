package com.coldguard.gateway.api.asset;

import com.coldguard.asset.grpc.v1.ChangeSensorStatusRequest;
import com.coldguard.asset.grpc.v1.CreateOrganizationRequest;
import com.coldguard.asset.grpc.v1.CreateSiteRequest;
import com.coldguard.asset.grpc.v1.GetSensorHistoryRequest;
import com.coldguard.asset.grpc.v1.ListAssetsRequest;
import com.coldguard.asset.grpc.v1.ListOrganizationsRequest;
import com.coldguard.asset.grpc.v1.ListSensorsRequest;
import com.coldguard.asset.grpc.v1.ListSitesRequest;
import com.coldguard.asset.grpc.v1.ReassignSensorRequest;
import com.coldguard.asset.grpc.v1.RecordCalibrationRequest;
import com.coldguard.asset.grpc.v1.RegisterAssetRequest;
import com.coldguard.asset.grpc.v1.RegisterSensorRequest;
import com.coldguard.asset.grpc.v1.RetireSensorRequest;
import com.coldguard.asset.grpc.v1.UpdateAssetRequest;
import com.coldguard.asset.grpc.v1.UpdateSensorRequest;
import com.coldguard.asset.grpc.v1.UpsertOperationalProfileRequest;
import com.coldguard.common.grpc.v1.CursorPageRequest;
import com.coldguard.common.grpc.v1.PageInfo;
import com.coldguard.common.grpc.v1.PageRequest;
import com.coldguard.gateway.api.common.PageResponse;
import com.google.protobuf.Duration;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST ↔ gRPC translation for the Asset resources. Both vocabularies stay independent: the REST
 * enums have no protobuf prefix and no REST type exposes a generated class. It carries no rule and
 * makes no decision; it only renames, converts units and passes through.
 */
final class AssetRestMapper {

  private static final String CRITICALITY = "CRITICALITY_";
  private static final String STATUS = "SENSOR_STATUS_";
  private static final String KIND = "CALIBRATION_KIND_";

  private AssetRestMapper() {}

  // ---- requests ---------------------------------------------------------------------------

  static CreateOrganizationRequest organization(
      com.coldguard.gateway.api.asset.CreateOrganizationRequest request) {
    return CreateOrganizationRequest.newBuilder().setName(request.name()).build();
  }

  static ListOrganizationsRequest listOrganizations(int page, int size) {
    return ListOrganizationsRequest.newBuilder().setPage(page(page, size)).build();
  }

  static CreateSiteRequest site(
      String organizationId, com.coldguard.gateway.api.asset.CreateSiteRequest request) {
    CreateSiteRequest.Builder builder =
        CreateSiteRequest.newBuilder().setOrganizationId(organizationId).setName(request.name());
    if (request.address() != null) {
      builder.setAddress(request.address());
    }
    return builder.build();
  }

  static ListSitesRequest listSites(String organizationId, int page, int size) {
    return ListSitesRequest.newBuilder()
        .setOrganizationId(organizationId)
        .setPage(page(page, size))
        .build();
  }

  static RegisterAssetRequest asset(com.coldguard.gateway.api.asset.RegisterAssetRequest request) {
    RegisterAssetRequest.Builder builder =
        RegisterAssetRequest.newBuilder()
            .setSiteId(request.siteId())
            .setName(request.name())
            .setCriticality(toGrpc(request.criticality()));
    if (request.description() != null) {
      builder.setDescription(request.description());
    }
    return builder.build();
  }

  static UpdateAssetRequest updateAsset(
      String assetId, com.coldguard.gateway.api.asset.UpdateAssetRequest request) {
    UpdateAssetRequest.Builder builder =
        UpdateAssetRequest.newBuilder()
            .setAssetId(assetId)
            .setExpectedVersion(request.expectedVersion());
    if (request.name() != null) {
      builder.setName(request.name());
    }
    if (request.description() != null) {
      builder.setDescription(request.description());
    }
    if (request.criticality() != null) {
      builder.setCriticality(toGrpc(request.criticality()));
    }
    return builder.build();
  }

  static ListAssetsRequest listAssets(String siteId, int page, int size) {
    ListAssetsRequest.Builder builder = ListAssetsRequest.newBuilder().setPage(page(page, size));
    if (siteId != null && !siteId.isBlank()) {
      builder.setSiteId(siteId);
    }
    return builder.build();
  }

  static RegisterSensorRequest sensor(
      com.coldguard.gateway.api.asset.RegisterSensorRequest request) {
    RegisterSensorRequest.Builder builder =
        RegisterSensorRequest.newBuilder()
            .setAssetId(request.assetId())
            .setSerialNumber(request.serialNumber())
            .setMeasurementUnit(request.measurementUnit());
    if (request.model() != null) {
      builder.setModel(request.model());
    }
    if (request.initialCalibration() != null) {
      var initial = request.initialCalibration();
      builder.setInitialCalibration(
          com.coldguard.asset.grpc.v1.InitialCalibration.newBuilder()
              .setKind(toGrpc(initial.kind()))
              .setPerformedAt(timestamp(initial.performedAt()))
              .setReason(initial.reason()));
    }
    if (request.profile() != null) {
      // The sensor does not exist yet; the service binds the profile to the new sensor.
      builder.setProfile(profile("", request.profile()));
    }
    return builder.build();
  }

  static UpdateSensorRequest updateSensor(
      String sensorId, com.coldguard.gateway.api.asset.UpdateSensorRequest request) {
    UpdateSensorRequest.Builder builder =
        UpdateSensorRequest.newBuilder()
            .setSensorId(sensorId)
            .setExpectedVersion(request.expectedVersion());
    if (request.serialNumber() != null) {
      builder.setSerialNumber(request.serialNumber());
    }
    if (request.model() != null) {
      builder.setModel(request.model());
    }
    return builder.build();
  }

  static ListSensorsRequest listSensors(String assetId, SensorStatus status, int page, int size) {
    ListSensorsRequest.Builder builder = ListSensorsRequest.newBuilder().setPage(page(page, size));
    if (assetId != null && !assetId.isBlank()) {
      builder.setAssetId(assetId);
    }
    if (status != null) {
      builder.setStatus(toGrpc(status));
    }
    return builder.build();
  }

  static UpsertOperationalProfileRequest upsertProfile(
      String sensorId, OperationalProfileRequest request) {
    return UpsertOperationalProfileRequest.newBuilder()
        .setProfile(profile(sensorId, request))
        .build();
  }

  static ChangeSensorStatusRequest changeStatus(String sensorId, StatusChangeRequest request) {
    return ChangeSensorStatusRequest.newBuilder()
        .setSensorId(sensorId)
        .setTargetStatus(toGrpc(request.targetStatus()))
        .setReason(request.reason())
        .build();
  }

  static RecordCalibrationRequest calibration(String sensorId, CalibrationRequest request) {
    return RecordCalibrationRequest.newBuilder()
        .setSensorId(sensorId)
        .setKind(toGrpc(request.kind()))
        .setPerformedAt(timestamp(request.performedAt()))
        .setReason(request.reason())
        .build();
  }

  static ReassignSensorRequest reassign(String sensorId, ReassignmentRequest request) {
    return ReassignSensorRequest.newBuilder()
        .setSensorId(sensorId)
        .setTargetAssetId(request.targetAssetId())
        .setReason(request.reason())
        .build();
  }

  static RetireSensorRequest retire(String sensorId, RetirementRequest request) {
    return RetireSensorRequest.newBuilder()
        .setSensorId(sensorId)
        .setReason(request.reason())
        .build();
  }

  static GetSensorHistoryRequest history(String sensorId, String cursor, int size) {
    return GetSensorHistoryRequest.newBuilder()
        .setSensorId(sensorId)
        .setPage(
            CursorPageRequest.newBuilder().setCursor(cursor == null ? "" : cursor).setSize(size))
        .build();
  }

  // ---- responses --------------------------------------------------------------------------

  static OrganizationResponse toRest(com.coldguard.asset.grpc.v1.Organization organization) {
    return new OrganizationResponse(
        organization.getId(), organization.getName(), instant(organization.getCreatedAt()));
  }

  static SiteResponse toRest(com.coldguard.asset.grpc.v1.Site site) {
    return new SiteResponse(
        site.getId(),
        site.getOrganizationId(),
        site.getName(),
        emptyToNull(site.getAddress()),
        instant(site.getCreatedAt()));
  }

  static AssetResponse toRest(com.coldguard.asset.grpc.v1.Asset asset) {
    return new AssetResponse(
        asset.getId(),
        asset.getSiteId(),
        asset.getName(),
        emptyToNull(asset.getDescription()),
        toRest(asset.getCriticality()),
        instant(asset.getCreatedAt()),
        instant(asset.getUpdatedAt()),
        asset.getVersion());
  }

  static SensorResponse toRest(com.coldguard.asset.grpc.v1.Sensor sensor) {
    return new SensorResponse(
        sensor.getId(),
        sensor.getSerialNumber(),
        emptyToNull(sensor.getModel()),
        sensor.getMeasurementUnit(),
        sensor.getAssetId(),
        toRest(sensor.getStatus()),
        instant(sensor.getStatusChangedAt()),
        sensor.hasLastCalibrationRecordedAt()
            ? instant(sensor.getLastCalibrationRecordedAt())
            : null,
        sensor.hasLastCalibrationValidUntil()
            ? instant(sensor.getLastCalibrationValidUntil())
            : null,
        instant(sensor.getCreatedAt()),
        instant(sensor.getUpdatedAt()),
        sensor.getVersion());
  }

  static OperationalProfileResponse toRest(com.coldguard.asset.grpc.v1.OperationalProfile profile) {
    var bands = profile.getMagnitudeBands();
    var persistence = profile.getPersistence();
    return new OperationalProfileResponse(
        profile.getSensorId(),
        profile.getMinTemperature(),
        profile.getMaxTemperature(),
        profile.getUnit(),
        new MagnitudeBands(bands.getMediumFrom(), bands.getHighFrom(), bands.getCriticalFrom()),
        new Persistence(
            persistence.getMinConsecutiveBreaches(), (int) persistence.getWindow().getSeconds()),
        (int) profile.getExpectedReadingInterval().getSeconds(),
        profile.hasCalibrationValidity() ? profile.getCalibrationValidity().getSeconds() : null,
        profile.getVersion(),
        instant(profile.getUpdatedAt()));
  }

  static CalibrationResponse toRest(com.coldguard.asset.grpc.v1.CalibrationRecord record) {
    return new CalibrationResponse(
        record.getId(),
        record.getSensorId(),
        toRest(record.getKind()),
        instant(record.getPerformedAt()),
        instant(record.getValidUntil()),
        instant(record.getRecordedAt()),
        record.getRecordedBy(),
        record.getReason());
  }

  static HistoryEntryResponse toRest(com.coldguard.asset.grpc.v1.SensorHistoryEntry entry) {
    return new HistoryEntryResponse(
        entry.getId(),
        entry.getAction(),
        entry.hasPreviousValue() ? toMap(entry.getPreviousValue()) : null,
        entry.hasNewValue() ? toMap(entry.getNewValue()) : null,
        emptyToNull(entry.getReason()),
        entry.getActorType(),
        entry.getActorId(),
        instant(entry.getOccurredAt()));
  }

  static PageResponse.PageInfo toRest(PageInfo page) {
    return new PageResponse.PageInfo(
        page.getPage(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  // ---- enums ------------------------------------------------------------------------------

  static com.coldguard.common.grpc.v1.Criticality toGrpc(Criticality criticality) {
    return com.coldguard.common.grpc.v1.Criticality.valueOf(CRITICALITY + criticality.name());
  }

  static Criticality toRest(com.coldguard.common.grpc.v1.Criticality criticality) {
    return Criticality.valueOf(strip(criticality.name(), CRITICALITY));
  }

  static com.coldguard.asset.grpc.v1.SensorStatus toGrpc(SensorStatus status) {
    return com.coldguard.asset.grpc.v1.SensorStatus.valueOf(STATUS + status.name());
  }

  static SensorStatus toRest(com.coldguard.asset.grpc.v1.SensorStatus status) {
    return SensorStatus.valueOf(strip(status.name(), STATUS));
  }

  static com.coldguard.asset.grpc.v1.CalibrationKind toGrpc(CalibrationKind kind) {
    return com.coldguard.asset.grpc.v1.CalibrationKind.valueOf(KIND + kind.name());
  }

  static CalibrationKind toRest(com.coldguard.asset.grpc.v1.CalibrationKind kind) {
    return CalibrationKind.valueOf(strip(kind.name(), KIND));
  }

  // ---- primitives -------------------------------------------------------------------------

  private static com.coldguard.asset.grpc.v1.OperationalProfile profile(
      String sensorId, OperationalProfileRequest request) {
    com.coldguard.asset.grpc.v1.OperationalProfile.Builder builder =
        com.coldguard.asset.grpc.v1.OperationalProfile.newBuilder()
            .setSensorId(sensorId)
            .setMinTemperature(request.minTemperature())
            .setMaxTemperature(request.maxTemperature())
            .setUnit(request.unit())
            .setMagnitudeBands(
                com.coldguard.asset.grpc.v1.MagnitudeBands.newBuilder()
                    .setMediumFrom(request.magnitudeBands().mediumFrom())
                    .setHighFrom(request.magnitudeBands().highFrom())
                    .setCriticalFrom(request.magnitudeBands().criticalFrom()))
            .setPersistence(
                com.coldguard.asset.grpc.v1.PersistenceWindow.newBuilder()
                    .setMinConsecutiveBreaches(request.persistence().minConsecutiveBreaches())
                    .setWindow(seconds(request.persistence().windowSeconds())))
            .setExpectedReadingInterval(seconds(request.expectedReadingIntervalSeconds()))
            .setVersion(request.version() == null ? 0 : request.version());
    if (request.calibrationValiditySeconds() != null) {
      builder.setCalibrationValidity(seconds(request.calibrationValiditySeconds()));
    }
    return builder.build();
  }

  private static PageRequest page(int page, int size) {
    return PageRequest.newBuilder().setPage(page).setSize(size).build();
  }

  private static Duration seconds(long seconds) {
    return Duration.newBuilder().setSeconds(seconds).build();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static String strip(String name, String prefix) {
    return name.substring(prefix.length());
  }

  private static Map<String, Object> toMap(Struct struct) {
    Map<String, Object> map = new LinkedHashMap<>();
    struct.getFieldsMap().forEach((key, value) -> map.put(key, toObject(value)));
    return map;
  }

  private static Object toObject(Value value) {
    return switch (value.getKindCase()) {
      case STRING_VALUE -> value.getStringValue();
      case NUMBER_VALUE -> {
        double number = value.getNumberValue();
        yield number == Math.rint(number) && Math.abs(number) < 1e15
            ? (Object) (long) number
            : number;
      }
      case BOOL_VALUE -> value.getBoolValue();
      case STRUCT_VALUE -> toMap(value.getStructValue());
      case LIST_VALUE -> {
        List<Object> items = new java.util.ArrayList<>();
        value.getListValue().getValuesList().forEach(item -> items.add(toObject(item)));
        yield items;
      }
      case NULL_VALUE, KIND_NOT_SET -> null;
    };
  }
}
