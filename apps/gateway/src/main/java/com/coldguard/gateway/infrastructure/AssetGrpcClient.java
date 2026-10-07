package com.coldguard.gateway.infrastructure;

import com.coldguard.asset.grpc.v1.Asset;
import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.CalibrationRecord;
import com.coldguard.asset.grpc.v1.ChangeSensorStatusRequest;
import com.coldguard.asset.grpc.v1.CreateOrganizationRequest;
import com.coldguard.asset.grpc.v1.CreateSiteRequest;
import com.coldguard.asset.grpc.v1.GetAssetRequest;
import com.coldguard.asset.grpc.v1.GetOperationalProfileRequest;
import com.coldguard.asset.grpc.v1.GetSensorHistoryRequest;
import com.coldguard.asset.grpc.v1.GetSensorHistoryResponse;
import com.coldguard.asset.grpc.v1.GetSensorRequest;
import com.coldguard.asset.grpc.v1.ListAssetsRequest;
import com.coldguard.asset.grpc.v1.ListAssetsResponse;
import com.coldguard.asset.grpc.v1.ListOrganizationsRequest;
import com.coldguard.asset.grpc.v1.ListOrganizationsResponse;
import com.coldguard.asset.grpc.v1.ListSensorsRequest;
import com.coldguard.asset.grpc.v1.ListSensorsResponse;
import com.coldguard.asset.grpc.v1.ListSitesRequest;
import com.coldguard.asset.grpc.v1.ListSitesResponse;
import com.coldguard.asset.grpc.v1.OperationalProfile;
import com.coldguard.asset.grpc.v1.Organization;
import com.coldguard.asset.grpc.v1.ReassignSensorRequest;
import com.coldguard.asset.grpc.v1.RecordCalibrationRequest;
import com.coldguard.asset.grpc.v1.RegisterAssetRequest;
import com.coldguard.asset.grpc.v1.RegisterSensorRequest;
import com.coldguard.asset.grpc.v1.RetireSensorRequest;
import com.coldguard.asset.grpc.v1.Sensor;
import com.coldguard.asset.grpc.v1.Site;
import com.coldguard.asset.grpc.v1.UpdateAssetRequest;
import com.coldguard.asset.grpc.v1.UpdateSensorRequest;
import com.coldguard.asset.grpc.v1.UpsertOperationalProfileRequest;
import com.coldguard.gateway.config.DownstreamProperties;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Thin adapter over the Asset Service stub: every call goes through {@link GrpcInvoker}, which
 * applies the configured deadline and turns a failure into a {@link DownstreamCallException}. The
 * caller's identity is not an argument: it travels as metadata, added by the global interceptor.
 */
@Component
public class AssetGrpcClient {

  static final String SERVICE = "asset-service";

  private final AssetServiceGrpc.AssetServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public AssetGrpcClient(
      AssetServiceGrpc.AssetServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  private <R> R query(Function<AssetServiceGrpc.AssetServiceBlockingStub, R> call) {
    return invoker.query(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  private <R> R call(Function<AssetServiceGrpc.AssetServiceBlockingStub, R> call) {
    return invoker.call(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  public Organization createOrganization(CreateOrganizationRequest request) {
    return call(s -> s.createOrganization(request));
  }

  public ListOrganizationsResponse listOrganizations(ListOrganizationsRequest request) {
    return query(s -> s.listOrganizations(request));
  }

  public Site createSite(CreateSiteRequest request) {
    return call(s -> s.createSite(request));
  }

  public ListSitesResponse listSites(ListSitesRequest request) {
    return query(s -> s.listSites(request));
  }

  public Asset registerAsset(RegisterAssetRequest request) {
    return call(s -> s.registerAsset(request));
  }

  public Asset updateAsset(UpdateAssetRequest request) {
    return call(s -> s.updateAsset(request));
  }

  public Asset getAsset(GetAssetRequest request) {
    return query(s -> s.getAsset(request));
  }

  public ListAssetsResponse listAssets(ListAssetsRequest request) {
    return query(s -> s.listAssets(request));
  }

  public Sensor registerSensor(RegisterSensorRequest request) {
    return call(s -> s.registerSensor(request));
  }

  public Sensor updateSensor(UpdateSensorRequest request) {
    return call(s -> s.updateSensor(request));
  }

  public Sensor getSensor(GetSensorRequest request) {
    return query(s -> s.getSensor(request));
  }

  public ListSensorsResponse listSensors(ListSensorsRequest request) {
    return query(s -> s.listSensors(request));
  }

  public OperationalProfile upsertOperationalProfile(UpsertOperationalProfileRequest request) {
    return call(s -> s.upsertOperationalProfile(request));
  }

  public OperationalProfile getOperationalProfile(GetOperationalProfileRequest request) {
    return query(s -> s.getOperationalProfile(request));
  }

  public Sensor changeSensorStatus(ChangeSensorStatusRequest request) {
    return call(s -> s.changeSensorStatus(request));
  }

  public CalibrationRecord recordCalibration(RecordCalibrationRequest request) {
    return call(s -> s.recordCalibration(request));
  }

  public Sensor reassignSensor(ReassignSensorRequest request) {
    return call(s -> s.reassignSensor(request));
  }

  public Sensor retireSensor(RetireSensorRequest request) {
    return call(s -> s.retireSensor(request));
  }

  public GetSensorHistoryResponse getSensorHistory(GetSensorHistoryRequest request) {
    return query(s -> s.getSensorHistory(request));
  }
}
