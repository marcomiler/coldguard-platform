package com.coldguard.gateway.api.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.common.grpc.v1.CursorPageInfo;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import com.coldguard.gateway.infrastructure.TelemetryGrpcClient;
import com.coldguard.telemetry.grpc.v1.ConnectivityStatus;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.IngestReadingsResponse;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusResponse;
import com.coldguard.telemetry.grpc.v1.ListReadingsRequest;
import com.coldguard.telemetry.grpc.v1.ListReadingsResponse;
import com.coldguard.telemetry.grpc.v1.MagnitudeLevel;
import com.coldguard.telemetry.grpc.v1.ReadingResult;
import com.coldguard.telemetry.grpc.v1.ReadingSource;
import com.coldguard.telemetry.grpc.v1.ReadingView;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The telemetry resources of the Gateway: routes, shape validation, REST vocabulary and the gRPC
 * call each makes.
 */
@WebMvcTest(controllers = TelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
class TelemetryRestApiTest {

  private static final String SENSOR = "3f1c2f7e-0000-4000-8000-000000000001";
  private static final Timestamp T0 = Timestamp.newBuilder().setSeconds(1_790_000_000L).build();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TelemetryGrpcClient telemetry;

  private static ReadingResult.Builder result(String id, ReadingResult.Outcome outcome) {
    return ReadingResult.newBuilder().setReadingId(id).setOutcome(outcome);
  }

  // ---- test readings ------------------------------------------------------------------------

  @Test
  void injectingAlwaysSendsTheTestInjectionSourceAndGeneratesWhatIsMissing() throws Exception {
    given(telemetry.ingestReadings(any()))
        .willReturn(
            IngestReadingsResponse.newBuilder()
                .addResults(
                    result("r-1", ReadingResult.Outcome.OUTCOME_ACCEPTED)
                        .setEligible(true)
                        .setBreached(true))
                .build());
    Instant before = Instant.now();

    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content(
                    "{\"readings\":[{\"sensorId\":\""
                        + SENSOR
                        + "\",\"value\":12.5,\"unit\":\"CELSIUS\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].outcome").value("ACCEPTED"))
        .andExpect(jsonPath("$.results[0].eligible").value(true))
        .andExpect(jsonPath("$.results[0].breached").value(true))
        .andExpect(jsonPath("$.results[0].rejectionCode").doesNotExist());

    var captor = ArgumentCaptor.forClass(IngestReadingsRequest.class);
    verify(telemetry).ingestReadings(captor.capture());
    IngestReadingsRequest request = captor.getValue();
    assertThat(request.getSource()).isEqualTo(ReadingSource.READING_SOURCE_TEST_INJECTION);
    var sent = request.getReadings(0);
    assertThat(UUID.fromString(sent.getReadingId())).isNotNull();
    assertThat(sent.getSensorId()).isEqualTo(SENSOR);
    assertThat(sent.getValue()).isEqualTo(12.5);
    assertThat(sent.getUnit()).isEqualTo("CELSIUS");
    Instant recordedAt =
        Instant.ofEpochSecond(sent.getRecordedAt().getSeconds(), sent.getRecordedAt().getNanos());
    assertThat(recordedAt).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
  }

  @Test
  void aReadingIdAndATimeSuppliedByTheClientAreKept() throws Exception {
    given(telemetry.ingestReadings(any())).willReturn(IngestReadingsResponse.getDefaultInstance());
    String id = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content(
                    "{\"readings\":[{\"readingId\":\""
                        + id
                        + "\",\"sensorId\":\""
                        + SENSOR
                        + "\",\"recordedAt\":\"2026-10-06T12:00:00Z\",\"value\":5,\"unit\":\"CELSIUS\"}]}"))
        .andExpect(status().isOk());

    var captor = ArgumentCaptor.forClass(IngestReadingsRequest.class);
    verify(telemetry).ingestReadings(captor.capture());
    assertThat(captor.getValue().getReadings(0).getReadingId()).isEqualTo(id);
    assertThat(captor.getValue().getReadings(0).getRecordedAt().getSeconds())
        .isEqualTo(Instant.parse("2026-10-06T12:00:00Z").getEpochSecond());
  }

  @Test
  void everyResultIsReportedInOrderWithItsOwnOutcomeAndRejectionCode() throws Exception {
    given(telemetry.ingestReadings(any()))
        .willReturn(
            IngestReadingsResponse.newBuilder()
                .addResults(result("a", ReadingResult.Outcome.OUTCOME_ACCEPTED).setEligible(true))
                .addResults(result("b", ReadingResult.Outcome.OUTCOME_DUPLICATE))
                .addResults(
                    result("c", ReadingResult.Outcome.OUTCOME_REJECTED)
                        .setRejectionCode("UNIT_MISMATCH"))
                .build());

    var response =
        mockMvc
            .perform(
                post("/api/v1/telemetry/test-readings")
                    .contentType("application/json")
                    .content(
                        "{\"readings\":["
                            + "{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"},"
                            + "{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"},"
                            + "{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].readingId").value("a"))
            .andExpect(jsonPath("$.results[1].outcome").value("DUPLICATE"))
            .andExpect(jsonPath("$.results[2].outcome").value("REJECTED"))
            .andExpect(jsonPath("$.results[2].rejectionCode").value("UNIT_MISMATCH"))
            .andReturn();

    assertThat(response.getResponse().getContentAsString()).doesNotContain("OUTCOME_");
  }

  @Test
  void aBatchNeedsReadingsAndEachNeedsItsShape() throws Exception {
    for (String body :
        new String[] {
          "{}",
          "{\"readings\":[]}",
          "{\"readings\":[{\"value\":1,\"unit\":\"C\"}]}",
          "{\"readings\":[{\"sensorId\":\"x\",\"unit\":\"C\"}]}",
          "{\"readings\":[{\"sensorId\":\"x\",\"value\":1}]}",
          "{\"readings\":[{\"sensorId\":\"x\",\"value\":1,\"unit\":\"" + "C".repeat(21) + "\"}]}"
        }) {
      mockMvc
          .perform(
              post("/api/v1/telemetry/test-readings").contentType("application/json").content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.errors[0].field").exists());
    }
    verify(telemetry, never()).ingestReadings(any());
  }

  @Test
  void theFieldOfAnInvalidReadingIsNamedByItsPosition() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content(
                    "{\"readings\":[{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"},{\"value\":1,\"unit\":\"C\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("readings[1].sensorId"));
  }

  @Test
  void moreThan500ReadingsIsRejectedWithoutCallingTelemetry() throws Exception {
    List<String> readings = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      readings.add("{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"}");
    }

    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content("{\"readings\":[" + String.join(",", readings) + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verify(telemetry, never()).ingestReadings(any());
  }

  @Test
  void aMalformedBodyIsRejectedWithoutEchoingIt() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content("{\"readings\": [oops secret"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  // ---- reading back -------------------------------------------------------------------------

  @Test
  void readingsAreListedWithTheRangeAndCursorForwardedAndTheRestVocabulary() throws Exception {
    given(telemetry.listReadings(any()))
        .willReturn(
            ListReadingsResponse.newBuilder()
                .addReadings(
                    ReadingView.newBuilder()
                        .setReadingId("r-1")
                        .setSensorId(SENSOR)
                        .setAssetId("a-1")
                        .setRecordedAt(T0)
                        .setReceivedAt(T0)
                        .setValue(12.5)
                        .setUnit("CELSIUS")
                        .setSource(ReadingSource.READING_SOURCE_TEST_INJECTION)
                        .setEligible(true)
                        .setBreached(true)
                        .setAnomalyType("TEMPERATURE_ABOVE_MAX")
                        .setMagnitude(MagnitudeLevel.MAGNITUDE_LEVEL_HIGH))
                .addReadings(
                    ReadingView.newBuilder()
                        .setReadingId("r-2")
                        .setSensorId(SENSOR)
                        .setAssetId("a-1")
                        .setRecordedAt(T0)
                        .setReceivedAt(T0)
                        .setValue(50)
                        .setUnit("CELSIUS")
                        .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
                        .setEligible(false)
                        .setIneligibilityReason("SENSOR_NOT_ACTIVE"))
                .setPage(CursorPageInfo.newBuilder().setNextCursor("next-1").setHasMore(true))
                .build());

    var result =
        mockMvc
            .perform(
                get("/api/v1/sensors/" + SENSOR + "/readings")
                    .param("from", "2026-10-06T00:00:00Z")
                    .param("to", "2026-10-06T23:59:59Z")
                    .param("cursor", "c-0")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].source").value("TEST_INJECTION"))
            .andExpect(jsonPath("$.items[0].breached").value(true))
            .andExpect(jsonPath("$.items[0].anomalyType").value("TEMPERATURE_ABOVE_MAX"))
            .andExpect(jsonPath("$.items[0].magnitude").value("HIGH"))
            .andExpect(jsonPath("$.items[0].ineligibilityReason").doesNotExist())
            .andExpect(jsonPath("$.items[1].source").value("SIMULATOR"))
            .andExpect(jsonPath("$.items[1].eligible").value(false))
            .andExpect(jsonPath("$.items[1].ineligibilityReason").value("SENSOR_NOT_ACTIVE"))
            .andExpect(jsonPath("$.items[1].magnitude").doesNotExist())
            .andExpect(jsonPath("$.nextCursor").value("next-1"))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("READING_SOURCE_")
        .doesNotContain("MAGNITUDE_LEVEL_");
    var captor = ArgumentCaptor.forClass(ListReadingsRequest.class);
    verify(telemetry).listReadings(captor.capture());
    assertThat(captor.getValue().getSensorId()).isEqualTo(SENSOR);
    assertThat(captor.getValue().getPage().getCursor()).isEqualTo("c-0");
    assertThat(captor.getValue().getPage().getSize()).isEqualTo(10);
    assertThat(
            Duration.ofSeconds(
                captor.getValue().getTo().getSeconds() - captor.getValue().getFrom().getSeconds()))
        .isEqualTo(Duration.ofHours(24).minusSeconds(1));
  }

  @Test
  void theRangeIsRequiredAndMustBeValidInstants() throws Exception {
    mockMvc
        .perform(get("/api/v1/sensors/" + SENSOR + "/readings").param("to", "2026-10-06T23:59:59Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("from")));
    mockMvc
        .perform(
            get("/api/v1/sensors/" + SENSOR + "/readings").param("from", "2026-10-06T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("to")));
    mockMvc
        .perform(
            get("/api/v1/sensors/" + SENSOR + "/readings")
                .param("from", "yesterday")
                .param("to", "2026-10-06T23:59:59Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verify(telemetry, never()).listReadings(any());
  }

  @Test
  void whatTelemetrySaysAboutTheRequestReachesTheClientWithItsCode() throws Exception {
    willThrow(
            new DownstreamCallException(
                "telemetry-service",
                Status.Code.INVALID_ARGUMENT,
                "RANGE_TOO_WIDE",
                "The range is at most PT168H",
                null))
        .given(telemetry)
        .listReadings(any());
    mockMvc
        .perform(
            get("/api/v1/sensors/" + SENSOR + "/readings")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-10-06T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RANGE_TOO_WIDE"));

    willThrow(
            new DownstreamCallException(
                "telemetry-service",
                Status.Code.INVALID_ARGUMENT,
                "BATCH_TOO_LARGE",
                "too many",
                null))
        .given(telemetry)
        .ingestReadings(any());
    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content("{\"readings\":[{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BATCH_TOO_LARGE"));
  }

  @Test
  void aTelemetryOutageIsA503WithoutExposingItsCause() throws Exception {
    willThrow(
            new DownstreamCallException(
                "telemetry-service",
                Status.Code.UNAVAILABLE,
                null,
                "io exception: refused telemetry-service:9092",
                null))
        .given(telemetry)
        .ingestReadings(any());

    mockMvc
        .perform(
            post("/api/v1/telemetry/test-readings")
                .contentType("application/json")
                .content("{\"readings\":[{\"sensorId\":\"x\",\"value\":1,\"unit\":\"C\"}]}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9092"))));
  }

  // ---- connectivity -------------------------------------------------------------------------

  @Test
  void connectivityIsServedByItsOwnRouteAndNotTakenForASensorId() throws Exception {
    given(telemetry.listConnectivityStatus(any()))
        .willReturn(
            ListConnectivityStatusResponse.newBuilder()
                .addStatuses(
                    ConnectivityStatus.newBuilder()
                        .setSensorId(SENSOR)
                        .setAssetId("a-1")
                        .setLastReadingAt(T0)
                        .setExpectedReadingInterval(
                            com.google.protobuf.Duration.newBuilder().setSeconds(30))
                        .setConnectivityLostAt(T0))
                .addStatuses(
                    ConnectivityStatus.newBuilder()
                        .setSensorId("s-2")
                        .setAssetId("a-1")
                        .setExpectedReadingInterval(
                            com.google.protobuf.Duration.newBuilder().setSeconds(60)))
                .setPage(
                    com.coldguard.common.grpc.v1.PageInfo.newBuilder()
                        .setPage(1)
                        .setSize(2)
                        .setTotalElements(3)
                        .setTotalPages(2))
                .build());

    mockMvc
        .perform(
            get("/api/v1/sensors/connectivity")
                .param("onlyLost", "true")
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sensorId").value(SENSOR))
        .andExpect(jsonPath("$.items[0].expectedReadingIntervalSeconds").value(30))
        .andExpect(jsonPath("$.items[0].connectivityLostAt").exists())
        .andExpect(jsonPath("$.items[1].lastReadingAt").doesNotExist())
        .andExpect(jsonPath("$.items[1].connectivityLostAt").doesNotExist())
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(2));

    var request =
        org.mockito.ArgumentCaptor.forClass(
            com.coldguard.telemetry.grpc.v1.ListConnectivityStatusRequest.class);
    org.mockito.Mockito.verify(telemetry).listConnectivityStatus(request.capture());
    assertThat(request.getValue().getOnlyLost()).isTrue();
    assertThat(request.getValue().getPage().getPage()).isEqualTo(1);
    assertThat(request.getValue().getPage().getSize()).isEqualTo(2);
  }

  @Test
  void connectivityDefaultsToEveryStatusAndTheFirstPage() throws Exception {
    given(telemetry.listConnectivityStatus(any()))
        .willReturn(ListConnectivityStatusResponse.getDefaultInstance());

    mockMvc.perform(get("/api/v1/sensors/connectivity")).andExpect(status().isOk());

    var request =
        org.mockito.ArgumentCaptor.forClass(
            com.coldguard.telemetry.grpc.v1.ListConnectivityStatusRequest.class);
    org.mockito.Mockito.verify(telemetry).listConnectivityStatus(request.capture());
    assertThat(request.getValue().getOnlyLost()).isFalse();
    assertThat(request.getValue().getPage().getPage()).isZero();
    assertThat(request.getValue().getPage().getSize()).isZero();
  }

  @Test
  void aConnectivityOutageIsA503() throws Exception {
    willThrow(
            new DownstreamCallException(
                "telemetry-service", Status.Code.UNAVAILABLE, null, "refused", null))
        .given(telemetry)
        .listConnectivityStatus(any());

    mockMvc
        .perform(get("/api/v1/sensors/connectivity"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
  }
}
