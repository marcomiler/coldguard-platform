package com.coldguard.telemetry.infrastructure;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.GetSensorEvaluationContextsRequest;
import com.coldguard.telemetry.application.AssetUnavailableException;
import com.coldguard.telemetry.domain.Criticality;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Asks Asset for the evaluation context of sensors, in as few calls as it allows, each with a
 * deadline. It is the only place that knows Asset's protocol; everything else sees domain types.
 * Any failure is reported as {@link AssetUnavailableException}: nothing can be decided without it.
 */
public class AssetContextClient {

  /** Asset answers at most this many sensors per call. */
  static final int MAX_PER_CALL = 500;

  private final AssetServiceGrpc.AssetServiceBlockingStub stub;
  private final Duration deadline;

  public AssetContextClient(AssetServiceGrpc.AssetServiceBlockingStub stub, Duration deadline) {
    this.stub = stub;
    this.deadline = deadline;
  }

  /** Sensors that do not exist are simply absent from the result. */
  public List<SensorContext> fetch(Collection<UUID> sensorIds) {
    List<UUID> ids = new ArrayList<>(sensorIds);
    List<SensorContext> found = new ArrayList<>();
    for (int from = 0; from < ids.size(); from += MAX_PER_CALL) {
      List<UUID> chunk = ids.subList(from, Math.min(from + MAX_PER_CALL, ids.size()));
      GetSensorEvaluationContextsRequest.Builder request =
          GetSensorEvaluationContextsRequest.newBuilder();
      chunk.forEach(id -> request.addSensorIds(id.toString()));
      try {
        stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
            .getSensorEvaluationContexts(request.build())
            .getContextsList()
            .forEach(context -> found.add(toDomain(context)));
      } catch (StatusRuntimeException e) {
        throw new AssetUnavailableException(
            "Asset could not provide the evaluation context (" + e.getStatus().getCode() + ")", e);
      }
    }
    return found;
  }

  private static SensorContext toDomain(
      com.coldguard.asset.grpc.v1.SensorEvaluationContext context) {
    EvaluationProfile profile = null;
    if (context.hasProfile()) {
      var p = context.getProfile();
      profile =
          new EvaluationProfile(
              BigDecimal.valueOf(p.getMinTemperature()),
              BigDecimal.valueOf(p.getMaxTemperature()),
              p.getUnit(),
              BigDecimal.valueOf(p.getMagnitudeBands().getMediumFrom()),
              BigDecimal.valueOf(p.getMagnitudeBands().getHighFrom()),
              BigDecimal.valueOf(p.getMagnitudeBands().getCriticalFrom()),
              p.getPersistence().getMinConsecutiveBreaches(),
              Duration.ofSeconds(
                  p.getPersistence().getWindow().getSeconds(),
                  p.getPersistence().getWindow().getNanos()),
              Duration.ofSeconds(
                  p.getExpectedReadingInterval().getSeconds(),
                  p.getExpectedReadingInterval().getNanos()));
    }
    return new SensorContext(
        UUID.fromString(context.getSensorId()),
        UUID.fromString(context.getAssetId()),
        criticality(context.getAssetCriticality()),
        status(context.getStatus()),
        profile);
  }

  private static Criticality criticality(com.coldguard.common.grpc.v1.Criticality criticality) {
    return switch (criticality) {
      case CRITICALITY_LOW -> Criticality.LOW;
      case CRITICALITY_MEDIUM -> Criticality.MEDIUM;
      case CRITICALITY_HIGH -> Criticality.HIGH;
      case CRITICALITY_CRITICAL -> Criticality.CRITICAL;
      case CRITICALITY_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalStateException("Asset sent a sensor without a criticality");
    };
  }

  private static SensorStatus status(com.coldguard.asset.grpc.v1.SensorStatus status) {
    return switch (status) {
      case SENSOR_STATUS_ACTIVE -> SensorStatus.ACTIVE;
      case SENSOR_STATUS_IN_MAINTENANCE -> SensorStatus.IN_MAINTENANCE;
      case SENSOR_STATUS_INACTIVE -> SensorStatus.INACTIVE;
      case SENSOR_STATUS_RETIRED -> SensorStatus.RETIRED;
      case SENSOR_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalStateException("Asset sent a sensor without a status");
    };
  }
}
