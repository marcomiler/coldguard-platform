package com.coldguard.asset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.application.AssetCatalogService;
import com.coldguard.asset.application.CalibrationPolicy;
import com.coldguard.asset.application.OperationalProfileService;
import com.coldguard.asset.application.PageRequestPolicy;
import com.coldguard.asset.application.SensorService;
import com.coldguard.asset.grpc.v1.Asset;
import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.CalibrationKind;
import com.coldguard.asset.grpc.v1.ChangeSensorStatusRequest;
import com.coldguard.asset.grpc.v1.CreateOrganizationRequest;
import com.coldguard.asset.grpc.v1.CreateSiteRequest;
import com.coldguard.asset.grpc.v1.GetOperationalProfileRequest;
import com.coldguard.asset.grpc.v1.InitialCalibration;
import com.coldguard.asset.grpc.v1.ListOrganizationsRequest;
import com.coldguard.asset.grpc.v1.ListSensorsRequest;
import com.coldguard.asset.grpc.v1.MagnitudeBands;
import com.coldguard.asset.grpc.v1.OperationalProfile;
import com.coldguard.asset.grpc.v1.PersistenceWindow;
import com.coldguard.asset.grpc.v1.RegisterAssetRequest;
import com.coldguard.asset.grpc.v1.RegisterSensorRequest;
import com.coldguard.asset.grpc.v1.Sensor;
import com.coldguard.asset.grpc.v1.SensorStatus;
import com.coldguard.asset.grpc.v1.UpdateAssetRequest;
import com.coldguard.asset.grpc.v1.UpsertOperationalProfileRequest;
import com.coldguard.asset.support.InMemoryAssetStore;
import com.coldguard.asset.support.RecordingEvents;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.commons.security.Role;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;

/**
 * The real service behind the same exception-handler interceptor used at runtime, over an
 * in-process transport: it proves statuses, business codes and the identity check end to end
 * without any manual exception mapping in the test.
 */
class AssetGrpcServerWiringTest {

  private static final Actor ADMIN = new Actor("admin-1", Set.of(Role.PLATFORM_ADMIN));
  private static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));

  private final AtomicReference<Actor> caller = new AtomicReference<>(ADMIN);
  private Server server;
  private ManagedChannel channel;
  private AssetServiceGrpc.AssetServiceBlockingStub stub;

  @BeforeEach
  void start() throws Exception {
    startWith(java.time.Duration.ofDays(90));
  }

  private void startWith(java.time.Duration defaultValidity) throws Exception {
    stop();
    InMemoryAssetStore store = new InMemoryAssetStore();
    var events = new RecordingEvents();
    Clock clock = Clock.systemUTC();
    PageRequestPolicy paging = new PageRequestPolicy(100, 20);
    var profiles =
        new OperationalProfileService(
            store.sensorRepository,
            store.profileRepository,
            store.historyRepository,
            events,
            clock);
    var service =
        new AssetGrpcService(
            new AssetCatalogService(
                store.organizationRepository,
                store.siteRepository,
                store.assetRepository,
                events,
                paging,
                clock),
            new SensorService(
                store.assetRepository,
                store.sensorRepository,
                store.calibrationRepository,
                store.historyRepository,
                profiles,
                new CalibrationPolicy(defaultValidity),
                events,
                paging,
                clock),
            profiles);
    String name = "asset-wiring-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                ServerInterceptors.intercept(
                    service,
                    actorFromTestState(),
                    new GrpcExceptionHandlerInterceptor(new AssetGrpcExceptionHandler())))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    stub = AssetServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void stop() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  private ServerInterceptor actorFromTestState() {
    return new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
          ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Context context =
            Context.current().withValue(ActorServerInterceptor.ACTOR_CONTEXT_KEY, caller.get());
        return Contexts.interceptCall(context, call, headers, next);
      }
    };
  }

  private static void assertFails(Runnable call, Status.Code code, String businessCode) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(code);
              if (businessCode != null) {
                assertThat(e.getTrailers().get(AssetGrpcExceptionHandler.ERROR_CODE))
                    .isEqualTo(businessCode);
              }
            });
  }

  private Asset anAsset() {
    var org =
        stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("Acme").build());
    var site =
        stub.createSite(
            CreateSiteRequest.newBuilder().setOrganizationId(org.getId()).setName("Main").build());
    return stub.registerAsset(
        RegisterAssetRequest.newBuilder()
            .setSiteId(site.getId())
            .setName("Cold room 1")
            .setCriticality(com.coldguard.common.grpc.v1.Criticality.CRITICALITY_HIGH)
            .build());
  }

  private static OperationalProfile profile(String sensorId, long version) {
    return OperationalProfile.newBuilder()
        .setSensorId(sensorId)
        .setMinTemperature(2.0)
        .setMaxTemperature(8.0)
        .setUnit("CELSIUS")
        .setMagnitudeBands(
            MagnitudeBands.newBuilder().setMediumFrom(1.0).setHighFrom(3.0).setCriticalFrom(6.0))
        .setPersistence(
            PersistenceWindow.newBuilder()
                .setMinConsecutiveBreaches(3)
                .setWindow(Duration.newBuilder().setSeconds(300)))
        .setExpectedReadingInterval(Duration.newBuilder().setSeconds(5))
        .setVersion(version)
        .build();
  }

  @Test
  void organizationsAreCreatedAndListedWithPaging() {
    stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("Acme").build());

    var page = stub.listOrganizations(ListOrganizationsRequest.getDefaultInstance());

    assertThat(page.getOrganizationsList()).extracting("name").containsExactly("Acme");
    assertThat(page.getPage().getTotalElements()).isEqualTo(1);
    assertThat(page.getPage().getSize()).isEqualTo(20);
  }

  @Test
  void withoutAnActorEverythingIsPermissionDenied() {
    caller.set(null);

    assertFails(
        () -> stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("A").build()),
        Status.Code.PERMISSION_DENIED,
        null);
    assertFails(
        () -> stub.listOrganizations(ListOrganizationsRequest.getDefaultInstance()),
        Status.Code.PERMISSION_DENIED,
        null);
  }

  @Test
  void aSupervisorMayReadButNotChange() {
    stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("Acme").build());
    caller.set(SUPERVISOR);

    assertThat(
            stub.listOrganizations(ListOrganizationsRequest.getDefaultInstance())
                .getOrganizationsCount())
        .isEqualTo(1);
    assertFails(
        () -> stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("B").build()),
        Status.Code.PERMISSION_DENIED,
        null);
  }

  @Test
  void duplicateNamesAreAlreadyExistsWithTheirCode() {
    stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("Acme").build());

    assertFails(
        () ->
            stub.createOrganization(CreateOrganizationRequest.newBuilder().setName("acme").build()),
        Status.Code.ALREADY_EXISTS,
        "ORGANIZATION_NAME_DUPLICATED");
  }

  @Test
  void unknownAndMalformedIdsAreNotFoundWithTheirCode() {
    assertFails(
        () ->
            stub.createSite(
                CreateSiteRequest.newBuilder()
                    .setOrganizationId(UUID.randomUUID().toString())
                    .setName("S")
                    .build()),
        Status.Code.NOT_FOUND,
        "ORGANIZATION_NOT_FOUND");
    assertFails(
        () ->
            stub.createSite(
                CreateSiteRequest.newBuilder()
                    .setOrganizationId("not-a-uuid")
                    .setName("S")
                    .build()),
        Status.Code.NOT_FOUND,
        "ORGANIZATION_NOT_FOUND");
    assertFails(
        () ->
            stub.registerAsset(
                RegisterAssetRequest.newBuilder()
                    .setSiteId(UUID.randomUUID().toString())
                    .setName("A")
                    .setCriticality(com.coldguard.common.grpc.v1.Criticality.CRITICALITY_LOW)
                    .build()),
        Status.Code.NOT_FOUND,
        "SITE_NOT_FOUND");
  }

  @Test
  void criticalityIsRequiredWhenRegisteringAnAsset() {
    Asset asset = anAsset();
    assertFails(
        () ->
            stub.registerAsset(
                RegisterAssetRequest.newBuilder()
                    .setSiteId(asset.getSiteId())
                    .setName("B")
                    .build()),
        Status.Code.INVALID_ARGUMENT,
        null);
  }

  @Test
  void updateAssetChangesOnlyTheFieldsThatAreSetAndGuardsTheVersion() {
    Asset asset = anAsset();
    assertThat(asset.getVersion()).isEqualTo(1);

    Asset updated =
        stub.updateAsset(
            UpdateAssetRequest.newBuilder()
                .setAssetId(asset.getId())
                .setExpectedVersion(1)
                .setCriticality(com.coldguard.common.grpc.v1.Criticality.CRITICALITY_CRITICAL)
                .build());

    assertThat(updated.getName()).isEqualTo("Cold room 1");
    assertThat(updated.getCriticality())
        .isEqualTo(com.coldguard.common.grpc.v1.Criticality.CRITICALITY_CRITICAL);
    assertThat(updated.getVersion()).isEqualTo(2);
    assertFails(
        () ->
            stub.updateAsset(
                UpdateAssetRequest.newBuilder()
                    .setAssetId(asset.getId())
                    .setExpectedVersion(1)
                    .setName("Late")
                    .build()),
        Status.Code.ABORTED,
        "CONCURRENT_MODIFICATION");
  }

  @Test
  void aSensorIsRegisteredWithItsCalibrationAndProfile() {
    Asset asset = anAsset();
    Instant performed = Instant.now().minusSeconds(60);

    Sensor sensor =
        stub.registerSensor(
            RegisterSensorRequest.newBuilder()
                .setAssetId(asset.getId())
                .setSerialNumber("SN-1")
                .setMeasurementUnit("CELSIUS")
                .setInitialCalibration(
                    InitialCalibration.newBuilder()
                        .setKind(CalibrationKind.CALIBRATION_KIND_CALIBRATION)
                        .setPerformedAt(
                            Timestamp.newBuilder().setSeconds(performed.getEpochSecond()))
                        .setReason("Factory"))
                .setProfile(profile(UUID.randomUUID().toString(), 0))
                .build());

    assertThat(sensor.getStatus()).isEqualTo(SensorStatus.SENSOR_STATUS_ACTIVE);
    assertThat(sensor.hasLastCalibrationValidUntil()).isTrue();
    assertThat(sensor.getLastCalibrationValidUntil().getSeconds())
        .isEqualTo(performed.plus(java.time.Duration.ofDays(90)).getEpochSecond());
    var stored =
        stub.getOperationalProfile(
            GetOperationalProfileRequest.newBuilder().setSensorId(sensor.getId()).build());
    assertThat(stored.getVersion()).isEqualTo(1);
    assertThat(stored.getMinTemperature()).isEqualTo(2.0);
    assertThat(stored.getMagnitudeBands().getCriticalFrom()).isEqualTo(6.0);
    assertThat(stored.getPersistence().getWindow().getSeconds()).isEqualTo(300);
    assertThat(stored.hasCalibrationValidity()).isFalse();
    assertThat(
            stub.listSensors(ListSensorsRequest.newBuilder().setAssetId(asset.getId()).build())
                .getSensorsCount())
        .isEqualTo(1);
  }

  @Test
  void upsertingAProfileWithAnOutdatedVersionIsAborted() {
    Asset asset = anAsset();
    Sensor sensor =
        stub.registerSensor(
            RegisterSensorRequest.newBuilder()
                .setAssetId(asset.getId())
                .setSerialNumber("SN-1")
                .setMeasurementUnit("CELSIUS")
                .build());
    stub.upsertOperationalProfile(
        UpsertOperationalProfileRequest.newBuilder()
            .setProfile(profile(sensor.getId(), 0))
            .build());

    assertFails(
        () ->
            stub.upsertOperationalProfile(
                UpsertOperationalProfileRequest.newBuilder()
                    .setProfile(profile(sensor.getId(), 0).toBuilder().setMinTemperature(1.0))
                    .build()),
        Status.Code.ABORTED,
        "CONCURRENT_MODIFICATION");
  }

  @Test
  void aSensorWithoutProfileHasNoProfileToRead() {
    Asset asset = anAsset();
    Sensor sensor =
        stub.registerSensor(
            RegisterSensorRequest.newBuilder()
                .setAssetId(asset.getId())
                .setSerialNumber("SN-1")
                .setMeasurementUnit("CELSIUS")
                .build());

    assertFails(
        () ->
            stub.getOperationalProfile(
                GetOperationalProfileRequest.newBuilder().setSensorId(sensor.getId()).build()),
        Status.Code.NOT_FOUND,
        "PROFILE_NOT_FOUND");
  }

  @Test
  void aCalibrationWithoutAnyConfiguredValidityIsAPreconditionFailure() throws Exception {
    startWith(null);
    Asset asset = anAsset();

    assertFails(
        () ->
            stub.registerSensor(
                RegisterSensorRequest.newBuilder()
                    .setAssetId(asset.getId())
                    .setSerialNumber("SN-1")
                    .setMeasurementUnit("CELSIUS")
                    .setInitialCalibration(
                        InitialCalibration.newBuilder()
                            .setKind(CalibrationKind.CALIBRATION_KIND_CALIBRATION)
                            .setPerformedAt(
                                Timestamp.newBuilder()
                                    .setSeconds(Instant.now().minusSeconds(5).getEpochSecond()))
                            .setReason("x"))
                    .build()),
        Status.Code.FAILED_PRECONDITION,
        "CALIBRATION_VALIDITY_NOT_CONFIGURED");
  }

  @Test
  void lifecycleRpcsAreNotImplementedYet() {
    assertFails(
        () ->
            stub.changeSensorStatus(
                ChangeSensorStatusRequest.newBuilder()
                    .setSensorId(UUID.randomUUID().toString())
                    .setTargetStatus(SensorStatus.SENSOR_STATUS_INACTIVE)
                    .setReason("x")
                    .build()),
        Status.Code.UNIMPLEMENTED,
        null);
  }
}
