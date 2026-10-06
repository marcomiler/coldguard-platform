package com.coldguard.asset.api;

import static com.coldguard.asset.api.AssetGrpcMapper.id;
import static com.coldguard.asset.api.AssetGrpcMapper.optionalId;
import static com.coldguard.asset.api.AssetGrpcMapper.toGrpc;

import com.coldguard.asset.application.AssetCatalogService;
import com.coldguard.asset.application.OperationalProfileService;
import com.coldguard.asset.application.SensorService;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.CreateOrganizationRequest;
import com.coldguard.asset.grpc.v1.CreateSiteRequest;
import com.coldguard.asset.grpc.v1.GetAssetRequest;
import com.coldguard.asset.grpc.v1.GetOperationalProfileRequest;
import com.coldguard.asset.grpc.v1.GetSensorRequest;
import com.coldguard.asset.grpc.v1.ListAssetsRequest;
import com.coldguard.asset.grpc.v1.ListAssetsResponse;
import com.coldguard.asset.grpc.v1.ListOrganizationsRequest;
import com.coldguard.asset.grpc.v1.ListOrganizationsResponse;
import com.coldguard.asset.grpc.v1.ListSensorsRequest;
import com.coldguard.asset.grpc.v1.ListSensorsResponse;
import com.coldguard.asset.grpc.v1.ListSitesRequest;
import com.coldguard.asset.grpc.v1.ListSitesResponse;
import com.coldguard.asset.grpc.v1.RegisterAssetRequest;
import com.coldguard.asset.grpc.v1.RegisterSensorRequest;
import com.coldguard.asset.grpc.v1.UpdateAssetRequest;
import com.coldguard.asset.grpc.v1.UpdateSensorRequest;
import com.coldguard.asset.grpc.v1.UpsertOperationalProfileRequest;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint of the Asset context (contracts/grpc/asset/v1). It only translates requests to use
 * case calls and results to responses; business errors propagate as exceptions and are mapped to
 * gRPC statuses by {@link AssetGrpcExceptionHandler}. RPCs not implemented yet keep the default
 * UNIMPLEMENTED answer.
 */
@GrpcService
public class AssetGrpcService extends AssetServiceGrpc.AssetServiceImplBase {

  private final AssetCatalogService catalog;
  private final SensorService sensors;
  private final OperationalProfileService profiles;

  public AssetGrpcService(
      AssetCatalogService catalog, SensorService sensors, OperationalProfileService profiles) {
    this.catalog = catalog;
    this.sensors = sensors;
    this.profiles = profiles;
  }

  private static Actor actor() {
    return ActorServerInterceptor.ACTOR_CONTEXT_KEY.get();
  }

  // ---- organizations and sites -----------------------------------------------------------

  @Override
  public void createOrganization(
      CreateOrganizationRequest request,
      StreamObserver<com.coldguard.asset.grpc.v1.Organization> observer) {
    reply(observer, toGrpc(catalog.createOrganization(actor(), request.getName())));
  }

  @Override
  public void listOrganizations(
      ListOrganizationsRequest request, StreamObserver<ListOrganizationsResponse> observer) {
    var page =
        catalog.listOrganizations(
            actor(), request.getPage().getPage(), request.getPage().getSize());
    ListOrganizationsResponse.Builder reply =
        ListOrganizationsResponse.newBuilder().setPage(AssetGrpcMapper.pageInfo(page));
    page.items().forEach(item -> reply.addOrganizations(toGrpc(item)));
    reply(observer, reply.build());
  }

  @Override
  public void createSite(
      CreateSiteRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Site> observer) {
    reply(
        observer,
        toGrpc(
            catalog.createSite(
                actor(),
                id("Organization", request.getOrganizationId()),
                request.getName(),
                request.getAddress())));
  }

  @Override
  public void listSites(ListSitesRequest request, StreamObserver<ListSitesResponse> observer) {
    var page =
        catalog.listSites(
            actor(),
            optionalId("Organization", request.getOrganizationId()),
            request.getPage().getPage(),
            request.getPage().getSize());
    ListSitesResponse.Builder reply =
        ListSitesResponse.newBuilder().setPage(AssetGrpcMapper.pageInfo(page));
    page.items().forEach(item -> reply.addSites(toGrpc(item)));
    reply(observer, reply.build());
  }

  // ---- assets -----------------------------------------------------------------------------

  @Override
  public void registerAsset(
      RegisterAssetRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Asset> observer) {
    reply(
        observer,
        toGrpc(
            catalog.registerAsset(
                actor(),
                id("Site", request.getSiteId()),
                request.getName(),
                request.getDescription(),
                AssetGrpcMapper.toDomain(request.getCriticality()))));
  }

  @Override
  public void updateAsset(
      UpdateAssetRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Asset> observer) {
    Criticality criticality =
        request.hasCriticality() ? AssetGrpcMapper.toDomain(request.getCriticality()) : null;
    reply(
        observer,
        toGrpc(
            catalog.updateAsset(
                actor(),
                id("Asset", request.getAssetId()),
                request.getExpectedVersion(),
                request.hasName() ? request.getName() : null,
                request.hasDescription() ? request.getDescription() : null,
                criticality)));
  }

  @Override
  public void getAsset(
      GetAssetRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Asset> observer) {
    reply(observer, toGrpc(catalog.getAsset(actor(), id("Asset", request.getAssetId()))));
  }

  @Override
  public void listAssets(ListAssetsRequest request, StreamObserver<ListAssetsResponse> observer) {
    var page =
        catalog.listAssets(
            actor(),
            request.hasSiteId() ? id("Site", request.getSiteId()) : null,
            request.getPage().getPage(),
            request.getPage().getSize());
    ListAssetsResponse.Builder reply =
        ListAssetsResponse.newBuilder().setPage(AssetGrpcMapper.pageInfo(page));
    page.items().forEach(item -> reply.addAssets(toGrpc(item)));
    reply(observer, reply.build());
  }

  // ---- sensors ----------------------------------------------------------------------------

  @Override
  public void registerSensor(
      RegisterSensorRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Sensor> observer) {
    SensorService.InitialCalibration calibration = null;
    if (request.hasInitialCalibration()) {
      var initial = request.getInitialCalibration();
      calibration =
          new SensorService.InitialCalibration(
              AssetGrpcMapper.toDomain(initial.getKind()),
              AssetGrpcMapper.toInstant(initial.getPerformedAt()),
              initial.getReason());
    }
    var profile =
        request.hasProfile()
            ? AssetGrpcMapper.toDraft(UUID.randomUUID(), request.getProfile())
            : null;
    reply(
        observer,
        toGrpc(
            sensors.register(
                actor(),
                id("Asset", request.getAssetId()),
                request.getSerialNumber(),
                request.getModel(),
                request.getMeasurementUnit(),
                calibration,
                profile)));
  }

  @Override
  public void updateSensor(
      UpdateSensorRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Sensor> observer) {
    reply(
        observer,
        toGrpc(
            sensors.update(
                actor(),
                id("Sensor", request.getSensorId()),
                request.getExpectedVersion(),
                request.hasSerialNumber() ? request.getSerialNumber() : null,
                request.hasModel() ? request.getModel() : null)));
  }

  @Override
  public void getSensor(
      GetSensorRequest request, StreamObserver<com.coldguard.asset.grpc.v1.Sensor> observer) {
    reply(observer, toGrpc(sensors.get(actor(), id("Sensor", request.getSensorId()))));
  }

  @Override
  public void listSensors(
      ListSensorsRequest request, StreamObserver<ListSensorsResponse> observer) {
    var page =
        sensors.list(
            actor(),
            request.hasAssetId() ? id("Asset", request.getAssetId()) : null,
            request.hasStatus() ? AssetGrpcMapper.toDomain(request.getStatus()) : null,
            request.getPage().getPage(),
            request.getPage().getSize());
    ListSensorsResponse.Builder reply =
        ListSensorsResponse.newBuilder().setPage(AssetGrpcMapper.pageInfo(page));
    page.items().forEach(item -> reply.addSensors(toGrpc(item)));
    reply(observer, reply.build());
  }

  // ---- operational profile ----------------------------------------------------------------

  @Override
  public void upsertOperationalProfile(
      UpsertOperationalProfileRequest request,
      StreamObserver<com.coldguard.asset.grpc.v1.OperationalProfile> observer) {
    var profile = request.getProfile();
    UUID sensorId = id("Sensor", profile.getSensorId());
    reply(
        observer,
        toGrpc(
            profiles.upsert(
                actor(), AssetGrpcMapper.toDraft(sensorId, profile), profile.getVersion())));
  }

  @Override
  public void getOperationalProfile(
      GetOperationalProfileRequest request,
      StreamObserver<com.coldguard.asset.grpc.v1.OperationalProfile> observer) {
    reply(observer, toGrpc(profiles.get(actor(), id("Sensor", request.getSensorId()))));
  }

  private static <T> void reply(StreamObserver<T> observer, T message) {
    observer.onNext(message);
    observer.onCompleted();
  }
}
