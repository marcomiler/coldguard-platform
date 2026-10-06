package com.coldguard.gateway.api.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.asset.grpc.v1.Asset;
import com.coldguard.asset.grpc.v1.CalibrationKind;
import com.coldguard.asset.grpc.v1.CalibrationRecord;
import com.coldguard.asset.grpc.v1.ChangeSensorStatusRequest;
import com.coldguard.asset.grpc.v1.GetSensorHistoryResponse;
import com.coldguard.asset.grpc.v1.ListAssetsResponse;
import com.coldguard.asset.grpc.v1.ListOrganizationsResponse;
import com.coldguard.asset.grpc.v1.ListSensorsRequest;
import com.coldguard.asset.grpc.v1.ListSensorsResponse;
import com.coldguard.asset.grpc.v1.ListSitesResponse;
import com.coldguard.asset.grpc.v1.MagnitudeBands;
import com.coldguard.asset.grpc.v1.OperationalProfile;
import com.coldguard.asset.grpc.v1.Organization;
import com.coldguard.asset.grpc.v1.PersistenceWindow;
import com.coldguard.asset.grpc.v1.RegisterAssetRequest;
import com.coldguard.asset.grpc.v1.RegisterSensorRequest;
import com.coldguard.asset.grpc.v1.Sensor;
import com.coldguard.asset.grpc.v1.SensorHistoryEntry;
import com.coldguard.asset.grpc.v1.SensorStatus;
import com.coldguard.asset.grpc.v1.Site;
import com.coldguard.asset.grpc.v1.UpdateAssetRequest;
import com.coldguard.asset.grpc.v1.UpdateSensorRequest;
import com.coldguard.asset.grpc.v1.UpsertOperationalProfileRequest;
import com.coldguard.common.grpc.v1.Criticality;
import com.coldguard.common.grpc.v1.CursorPageInfo;
import com.coldguard.common.grpc.v1.PageInfo;
import com.coldguard.gateway.infrastructure.AssetGrpcClient;
import com.google.protobuf.Duration;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Asset resources of the Gateway: routes, shape validation, REST vocabulary and the exact gRPC
 * request each call produces. Security is off here (it has its own tests).
 */
@WebMvcTest(
    controllers = {OrganizationController.class, AssetController.class, SensorController.class})
@AutoConfigureMockMvc(addFilters = false)
class AssetRestApiTest {

  private static final String ID = "3f1c2f7e-0000-4000-8000-000000000001";
  private static final Timestamp T0 = Timestamp.newBuilder().setSeconds(1_790_000_000L).build();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssetGrpcClient asset;

  private static Asset anAsset() {
    return Asset.newBuilder()
        .setId(ID)
        .setSiteId("site-1")
        .setName("Cold room 1")
        .setDescription("Vaccines")
        .setCriticality(Criticality.CRITICALITY_HIGH)
        .setCreatedAt(T0)
        .setUpdatedAt(T0)
        .setVersion(1)
        .build();
  }

  private static Sensor aSensor() {
    return Sensor.newBuilder()
        .setId(ID)
        .setSerialNumber("SN-1")
        .setMeasurementUnit("CELSIUS")
        .setAssetId("asset-1")
        .setStatus(SensorStatus.SENSOR_STATUS_IN_MAINTENANCE)
        .setStatusChangedAt(T0)
        .setLastCalibrationRecordedAt(T0)
        .setLastCalibrationValidUntil(T0)
        .setCreatedAt(T0)
        .setUpdatedAt(T0)
        .setVersion(3)
        .build();
  }

  private static PageInfo page() {
    return PageInfo.newBuilder()
        .setPage(1)
        .setSize(5)
        .setTotalElements(11)
        .setTotalPages(3)
        .build();
  }

  // ---- organizations and sites -------------------------------------------------------------

  @Test
  void createOrganization_is201AndPassesTheName() throws Exception {
    given(asset.createOrganization(any()))
        .willReturn(Organization.newBuilder().setId(ID).setName("Acme").setCreatedAt(T0).build());

    mockMvc
        .perform(
            post("/api/v1/organizations")
                .contentType("application/json")
                .content("{\"name\":\"Acme\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.name").value("Acme"))
        .andExpect(jsonPath("$.createdAt").value("2026-09-21T14:13:20Z"));

    var request =
        ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.CreateOrganizationRequest.class);
    verify(asset).createOrganization(request.capture());
    assertThat(request.getValue().getName()).isEqualTo("Acme");
  }

  @Test
  void listOrganizations_wrapsTheItemsInThePageEnvelopeAndForwardsPaging() throws Exception {
    given(asset.listOrganizations(any()))
        .willReturn(
            ListOrganizationsResponse.newBuilder()
                .addOrganizations(
                    Organization.newBuilder().setId(ID).setName("Acme").setCreatedAt(T0))
                .setPage(page())
                .build());

    mockMvc
        .perform(get("/api/v1/organizations").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Acme"))
        .andExpect(jsonPath("$.page.page").value(1))
        .andExpect(jsonPath("$.page.size").value(5))
        .andExpect(jsonPath("$.page.totalElements").value(11))
        .andExpect(jsonPath("$.page.totalPages").value(3));

    var request =
        ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.ListOrganizationsRequest.class);
    verify(asset).listOrganizations(request.capture());
    assertThat(request.getValue().getPage().getPage()).isEqualTo(1);
    assertThat(request.getValue().getPage().getSize()).isEqualTo(5);
  }

  @Test
  void anOrganizationNeedsANameOfAtMost120Characters() throws Exception {
    for (String body :
        new String[] {"{}", "{\"name\":\" \"}", "{\"name\":\"" + "x".repeat(121) + "\"}"}) {
      mockMvc
          .perform(post("/api/v1/organizations").contentType("application/json").content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
    verify(asset, never()).createOrganization(any());
  }

  @Test
  void siteRoutesAreNestedUnderTheOrganization() throws Exception {
    given(asset.createSite(any()))
        .willReturn(
            Site.newBuilder()
                .setId(ID)
                .setOrganizationId("org-1")
                .setName("Main")
                .setAddress("1 Cold St")
                .setCreatedAt(T0)
                .build());
    given(asset.listSites(any()))
        .willReturn(
            ListSitesResponse.newBuilder()
                .addSites(
                    Site.newBuilder()
                        .setId(ID)
                        .setOrganizationId("org-1")
                        .setName("Main")
                        .setCreatedAt(T0))
                .setPage(page())
                .build());

    mockMvc
        .perform(
            post("/api/v1/organizations/org-1/sites")
                .contentType("application/json")
                .content("{\"name\":\"Main\",\"address\":\"1 Cold St\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.organizationId").value("org-1"))
        .andExpect(jsonPath("$.address").value("1 Cold St"));
    mockMvc
        .perform(get("/api/v1/organizations/org-1/sites"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].address").doesNotExist());

    var request = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.CreateSiteRequest.class);
    verify(asset).createSite(request.capture());
    assertThat(request.getValue().getOrganizationId()).isEqualTo("org-1");
    var list = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.ListSitesRequest.class);
    verify(asset).listSites(list.capture());
    assertThat(list.getValue().getOrganizationId()).isEqualTo("org-1");
  }

  // ---- assets ------------------------------------------------------------------------------

  @Test
  void registerAsset_is201WithALocationAndSpeaksTheRestVocabulary() throws Exception {
    given(asset.registerAsset(any())).willReturn(anAsset());

    var result =
        mockMvc
            .perform(
                post("/api/v1/assets")
                    .contentType("application/json")
                    .content(
                        "{\"siteId\":\"site-1\",\"name\":\"Cold room 1\",\"criticality\":\"HIGH\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/assets/" + ID))
            .andExpect(jsonPath("$.criticality").value("HIGH"))
            .andExpect(jsonPath("$.version").value(1))
            .andReturn();

    var request = ArgumentCaptor.forClass(RegisterAssetRequest.class);
    verify(asset).registerAsset(request.capture());
    assertThat(request.getValue().getCriticality()).isEqualTo(Criticality.CRITICALITY_HIGH);
    assertThat(request.getValue().getSiteId()).isEqualTo("site-1");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("CRITICALITY_");
  }

  @Test
  void aCriticalityOutsideTheRestVocabularyIsRejected() throws Exception {
    for (String criticality : new String[] {"CRITICALITY_HIGH", "URGENT", "high"}) {
      mockMvc
          .perform(
              post("/api/v1/assets")
                  .contentType("application/json")
                  .content(
                      "{\"siteId\":\"s\",\"name\":\"A\",\"criticality\":\"" + criticality + "\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }
    mockMvc
        .perform(
            post("/api/v1/assets")
                .contentType("application/json")
                .content("{\"siteId\":\"s\",\"name\":\"A\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("criticality"));
    verify(asset, never()).registerAsset(any());
  }

  @Test
  void patchAssetSendsOnlyTheFieldsThatWereGiven() throws Exception {
    given(asset.updateAsset(any())).willReturn(anAsset());

    mockMvc
        .perform(
            patch("/api/v1/assets/" + ID)
                .contentType("application/json")
                .content("{\"expectedVersion\":4,\"criticality\":\"CRITICAL\"}"))
        .andExpect(status().isOk());

    var request = ArgumentCaptor.forClass(UpdateAssetRequest.class);
    verify(asset).updateAsset(request.capture());
    assertThat(request.getValue().getAssetId()).isEqualTo(ID);
    assertThat(request.getValue().getExpectedVersion()).isEqualTo(4);
    assertThat(request.getValue().hasCriticality()).isTrue();
    assertThat(request.getValue().getCriticality()).isEqualTo(Criticality.CRITICALITY_CRITICAL);
    assertThat(request.getValue().hasName()).isFalse();
    assertThat(request.getValue().hasDescription()).isFalse();
  }

  @Test
  void patchAssetNeedsTheExpectedVersion() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/assets/" + ID)
                .contentType("application/json")
                .content("{\"name\":\"New\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
    verify(asset, never()).updateAsset(any());
  }

  @Test
  void listAndGetAssets() throws Exception {
    given(asset.listAssets(any()))
        .willReturn(ListAssetsResponse.newBuilder().addAssets(anAsset()).setPage(page()).build());
    given(asset.getAsset(any())).willReturn(anAsset());

    mockMvc
        .perform(get("/api/v1/assets").param("siteId", "site-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Cold room 1"))
        .andExpect(jsonPath("$.page.totalPages").value(3));
    mockMvc
        .perform(get("/api/v1/assets/" + ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Vaccines"));

    var list = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.ListAssetsRequest.class);
    verify(asset).listAssets(list.capture());
    assertThat(list.getValue().getSiteId()).isEqualTo("site-1");
  }

  // ---- sensors -----------------------------------------------------------------------------

  @Test
  void registerSensor_withCalibrationAndProfile_mapsEverythingAndIs201() throws Exception {
    given(asset.registerSensor(any())).willReturn(aSensor());

    mockMvc
        .perform(
            post("/api/v1/sensors")
                .contentType("application/json")
                .content(
                    """
                    {"assetId":"asset-1","serialNumber":"SN-1","measurementUnit":"CELSIUS",
                     "initialCalibration":{"kind":"VERIFICATION","performedAt":"2026-09-21T14:00:00Z","reason":"Factory"},
                     "profile":{"minTemperature":2,"maxTemperature":8,"unit":"CELSIUS",
                       "magnitudeBands":{"mediumFrom":1,"highFrom":3,"criticalFrom":6},
                       "persistence":{"minConsecutiveBreaches":3,"windowSeconds":300},
                       "expectedReadingIntervalSeconds":5,"calibrationValiditySeconds":7776000}}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/sensors/" + ID))
        .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"));

    var captor = ArgumentCaptor.forClass(RegisterSensorRequest.class);
    verify(asset).registerSensor(captor.capture());
    RegisterSensorRequest request = captor.getValue();
    assertThat(request.getInitialCalibration().getKind())
        .isEqualTo(CalibrationKind.CALIBRATION_KIND_VERIFICATION);
    assertThat(request.getInitialCalibration().getPerformedAt().getSeconds())
        .isEqualTo(java.time.Instant.parse("2026-09-21T14:00:00Z").getEpochSecond());
    assertThat(request.getProfile().getPersistence().getWindow().getSeconds()).isEqualTo(300);
    assertThat(request.getProfile().getExpectedReadingInterval().getSeconds()).isEqualTo(5);
    assertThat(request.getProfile().getCalibrationValidity().getSeconds()).isEqualTo(7_776_000);
    assertThat(request.getProfile().getVersion()).isZero();
  }

  @Test
  void registerSensor_withoutCalibrationOrProfileSendsNeither() throws Exception {
    given(asset.registerSensor(any())).willReturn(aSensor());

    mockMvc
        .perform(
            post("/api/v1/sensors")
                .contentType("application/json")
                .content(
                    "{\"assetId\":\"a\",\"serialNumber\":\"SN\",\"measurementUnit\":\"CELSIUS\"}"))
        .andExpect(status().isCreated());

    var captor = ArgumentCaptor.forClass(RegisterSensorRequest.class);
    verify(asset).registerSensor(captor.capture());
    assertThat(captor.getValue().hasInitialCalibration()).isFalse();
    assertThat(captor.getValue().hasProfile()).isFalse();
    assertThat(captor.getValue().getModel()).isEmpty();
  }

  @Test
  void registerSensor_validatesTheShapeOfTheNestedObjects() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sensors")
                .contentType("application/json")
                .content(
                    "{\"assetId\":\"a\",\"serialNumber\":\"SN\",\"measurementUnit\":\"CELSIUS\","
                        + "\"initialCalibration\":{\"kind\":\"CALIBRATION\"},"
                        + "\"profile\":{\"minTemperature\":2}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[?(@.field=='initialCalibration.performedAt')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='profile.magnitudeBands')]").exists());
    verify(asset, never()).registerSensor(any());
  }

  @Test
  void listSensors_filtersByAssetAndStatusUsingTheRestStatusName() throws Exception {
    given(asset.listSensors(any()))
        .willReturn(ListSensorsResponse.newBuilder().addSensors(aSensor()).setPage(page()).build());

    mockMvc
        .perform(get("/api/v1/sensors").param("assetId", "asset-1").param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].serialNumber").value("SN-1"));

    var captor = ArgumentCaptor.forClass(ListSensorsRequest.class);
    verify(asset).listSensors(captor.capture());
    assertThat(captor.getValue().getAssetId()).isEqualTo("asset-1");
    assertThat(captor.getValue().getStatus()).isEqualTo(SensorStatus.SENSOR_STATUS_ACTIVE);
  }

  @Test
  void aStatusFilterOutsideTheRestVocabularyIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/sensors").param("status", "SENSOR_STATUS_ACTIVE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SENSOR_STATUS_ACTIVE\"}"))));
    verify(asset, never()).listSensors(any());
  }

  @Test
  void patchSensor_updatesTechnicalDataOnly() throws Exception {
    given(asset.updateSensor(any())).willReturn(aSensor());

    mockMvc
        .perform(
            patch("/api/v1/sensors/" + ID)
                .contentType("application/json")
                .content("{\"expectedVersion\":3,\"model\":\"Probe Y\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));

    var captor = ArgumentCaptor.forClass(UpdateSensorRequest.class);
    verify(asset).updateSensor(captor.capture());
    assertThat(captor.getValue().getModel()).isEqualTo("Probe Y");
    assertThat(captor.getValue().hasSerialNumber()).isFalse();
  }

  // ---- profile -----------------------------------------------------------------------------

  private static OperationalProfile aProfile(boolean withValidity) {
    OperationalProfile.Builder builder =
        OperationalProfile.newBuilder()
            .setSensorId(ID)
            .setMinTemperature(2)
            .setMaxTemperature(8)
            .setUnit("CELSIUS")
            .setMagnitudeBands(
                MagnitudeBands.newBuilder().setMediumFrom(1).setHighFrom(3).setCriticalFrom(6))
            .setPersistence(
                PersistenceWindow.newBuilder()
                    .setMinConsecutiveBreaches(3)
                    .setWindow(Duration.newBuilder().setSeconds(300)))
            .setExpectedReadingInterval(Duration.newBuilder().setSeconds(5))
            .setVersion(2)
            .setUpdatedAt(T0);
    if (withValidity) {
      builder.setCalibrationValidity(Duration.newBuilder().setSeconds(86_400));
    }
    return builder.build();
  }

  @Test
  void getProfile_exposesDurationsAsSecondsAndOmitsAnUnsetValidity() throws Exception {
    given(asset.getOperationalProfile(any())).willReturn(aProfile(false));

    mockMvc
        .perform(get("/api/v1/sensors/" + ID + "/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minTemperature").value(2.0))
        .andExpect(jsonPath("$.magnitudeBands.criticalFrom").value(6.0))
        .andExpect(jsonPath("$.persistence.minConsecutiveBreaches").value(3))
        .andExpect(jsonPath("$.persistence.windowSeconds").value(300))
        .andExpect(jsonPath("$.expectedReadingIntervalSeconds").value(5))
        .andExpect(jsonPath("$.calibrationValiditySeconds").doesNotExist())
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void putProfile_sendsTheExpectedVersionAndDefaultsItToZeroWhenCreating() throws Exception {
    given(asset.upsertOperationalProfile(any())).willReturn(aProfile(true));
    String body =
        "{\"minTemperature\":2,\"maxTemperature\":8,\"unit\":\"CELSIUS\","
            + "\"magnitudeBands\":{\"mediumFrom\":1,\"highFrom\":3,\"criticalFrom\":6},"
            + "\"persistence\":{\"minConsecutiveBreaches\":3,\"windowSeconds\":300},"
            + "\"expectedReadingIntervalSeconds\":5";

    mockMvc
        .perform(
            put("/api/v1/sensors/" + ID + "/profile")
                .contentType("application/json")
                .content(body + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calibrationValiditySeconds").value(86400));
    mockMvc
        .perform(
            put("/api/v1/sensors/" + ID + "/profile")
                .contentType("application/json")
                .content(body + ",\"version\":2}"))
        .andExpect(status().isOk());

    var captor = ArgumentCaptor.forClass(UpsertOperationalProfileRequest.class);
    verify(asset, org.mockito.Mockito.times(2)).upsertOperationalProfile(captor.capture());
    assertThat(captor.getAllValues().get(0).getProfile().getSensorId()).isEqualTo(ID);
    assertThat(captor.getAllValues().get(0).getProfile().getVersion()).isZero();
    assertThat(captor.getAllValues().get(1).getProfile().getVersion()).isEqualTo(2);
  }

  // ---- lifecycle ---------------------------------------------------------------------------

  @Test
  void changeStatus_mapsTheRestStatusAndReturnsTheSensor() throws Exception {
    given(asset.changeSensorStatus(any())).willReturn(aSensor());

    var result =
        mockMvc
            .perform(
                post("/api/v1/sensors/" + ID + "/status")
                    .contentType("application/json")
                    .content("{\"targetStatus\":\"IN_MAINTENANCE\",\"reason\":\"drifting\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
            .andReturn();

    var captor = ArgumentCaptor.forClass(ChangeSensorStatusRequest.class);
    verify(asset).changeSensorStatus(captor.capture());
    assertThat(captor.getValue().getTargetStatus())
        .isEqualTo(SensorStatus.SENSOR_STATUS_IN_MAINTENANCE);
    assertThat(captor.getValue().getReason()).isEqualTo("drifting");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("SENSOR_STATUS_");
  }

  @Test
  void everyLifecycleActionNeedsAReason() throws Exception {
    for (String path : new String[] {"status", "reassignment", "retirement", "calibrations"}) {
      mockMvc
          .perform(
              post("/api/v1/sensors/" + ID + "/" + path)
                  .contentType("application/json")
                  .content(
                      "{\"targetStatus\":\"INACTIVE\",\"targetAssetId\":\"a\",\"kind\":\"CALIBRATION\",\"performedAt\":\"2026-09-21T14:00:00Z\",\"reason\":\" \"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }
    verify(asset, never()).changeSensorStatus(any());
    verify(asset, never()).retireSensor(any());
    verify(asset, never()).reassignSensor(any());
    verify(asset, never()).recordCalibration(any());
  }

  @Test
  void recordCalibration_is201AndNeverCarriesAnExpiryFromTheClient() throws Exception {
    given(asset.recordCalibration(any()))
        .willReturn(
            CalibrationRecord.newBuilder()
                .setId("cal-1")
                .setSensorId(ID)
                .setKind(CalibrationKind.CALIBRATION_KIND_CALIBRATION)
                .setPerformedAt(T0)
                .setValidUntil(T0)
                .setRecordedAt(T0)
                .setRecordedBy("admin-1")
                .setReason("Recalibrated")
                .build());

    var result =
        mockMvc
            .perform(
                post("/api/v1/sensors/" + ID + "/calibrations")
                    .contentType("application/json")
                    .content(
                        "{\"kind\":\"CALIBRATION\",\"performedAt\":\"2026-09-21T14:00:00Z\",\"reason\":\"Recalibrated\",\"validUntil\":\"2030-01-01T00:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("CALIBRATION"))
            .andExpect(jsonPath("$.recordedBy").value("admin-1"))
            .andReturn();

    var captor =
        ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.RecordCalibrationRequest.class);
    verify(asset).recordCalibration(captor.capture());
    assertThat(captor.getValue().getSensorId()).isEqualTo(ID);
    assertThat(captor.getValue().getKind()).isEqualTo(CalibrationKind.CALIBRATION_KIND_CALIBRATION);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("CALIBRATION_KIND_");
  }

  @Test
  void reassignAndRetireReturnTheSensor() throws Exception {
    given(asset.reassignSensor(any())).willReturn(aSensor());
    given(asset.retireSensor(any())).willReturn(aSensor());

    mockMvc
        .perform(
            post("/api/v1/sensors/" + ID + "/reassignment")
                .contentType("application/json")
                .content("{\"targetAssetId\":\"asset-2\",\"reason\":\"moved\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/sensors/" + ID + "/retirement")
                .contentType("application/json")
                .content("{\"reason\":\"end of life\"}"))
        .andExpect(status().isOk());

    var reassign = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.ReassignSensorRequest.class);
    verify(asset).reassignSensor(reassign.capture());
    assertThat(reassign.getValue().getTargetAssetId()).isEqualTo("asset-2");
    var retire = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.RetireSensorRequest.class);
    verify(asset).retireSensor(retire.capture());
    assertThat(retire.getValue().getReason()).isEqualTo("end of life");
  }

  @Test
  void history_isPagedByCursorAndRendersTheBeforeAndAfterValues() throws Exception {
    given(asset.getSensorHistory(any()))
        .willReturn(
            GetSensorHistoryResponse.newBuilder()
                .addEntries(
                    SensorHistoryEntry.newBuilder()
                        .setId("h-1")
                        .setAction("STATUS_CHANGED")
                        .setPreviousValue(
                            Struct.newBuilder()
                                .putFields(
                                    "status", Value.newBuilder().setStringValue("ACTIVE").build()))
                        .setNewValue(
                            Struct.newBuilder()
                                .putFields(
                                    "status", Value.newBuilder().setStringValue("INACTIVE").build())
                                .putFields("version", Value.newBuilder().setNumberValue(3).build()))
                        .setReason("off")
                        .setActorType("SYSTEM")
                        .setActorId("calibration-expiry-job")
                        .setOccurredAt(T0))
                .setPage(CursorPageInfo.newBuilder().setNextCursor("next-1").setHasMore(true))
                .build());

    mockMvc
        .perform(
            get("/api/v1/sensors/" + ID + "/history").param("cursor", "c-0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("STATUS_CHANGED"))
        .andExpect(jsonPath("$.items[0].previousValue.status").value("ACTIVE"))
        .andExpect(jsonPath("$.items[0].newValue.status").value("INACTIVE"))
        .andExpect(jsonPath("$.items[0].newValue.version").value(3))
        .andExpect(jsonPath("$.items[0].actorType").value("SYSTEM"))
        .andExpect(jsonPath("$.items[0].actorId").value("calibration-expiry-job"))
        .andExpect(jsonPath("$.nextCursor").value("next-1"))
        .andExpect(jsonPath("$.hasMore").value(true));

    var captor = ArgumentCaptor.forClass(com.coldguard.asset.grpc.v1.GetSensorHistoryRequest.class);
    verify(asset).getSensorHistory(captor.capture());
    assertThat(captor.getValue().getPage().getCursor()).isEqualTo("c-0");
    assertThat(captor.getValue().getPage().getSize()).isEqualTo(10);
  }
}
