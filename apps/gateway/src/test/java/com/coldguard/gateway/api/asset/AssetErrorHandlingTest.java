package com.coldguard.gateway.api.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.commons.correlation.CorrelationIdFilter;
import com.coldguard.gateway.api.error.ApiExceptionHandler;
import com.coldguard.gateway.infrastructure.AssetGrpcClient;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Every failure of the Asset service reaches the client as Problem Details with a stable code. */
class AssetErrorHandlingTest {

  private static final String ID = "3f1c2f7e-0000-4000-8000-000000000001";

  private AssetGrpcClient asset;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    asset = mock(AssetGrpcClient.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SensorController(asset))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
  }

  private void fails(Status.Code code, String errorCode, String description) {
    org.mockito.BDDMockito.willThrow(
            new DownstreamCallException("asset-service", code, errorCode, description, null))
        .given(asset)
        .getSensor(any());
  }

  @Test
  void aBusinessErrorKeepsItsPublishedCodeAndDescription() throws Exception {
    fails(Status.Code.NOT_FOUND, "SENSOR_NOT_FOUND", "Sensor not found: " + ID);

    mockMvc
        .perform(get("/api/v1/sensors/" + ID))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("SENSOR_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Sensor not found: " + ID));
  }

  @Test
  void aRejectedTransitionIsAConflictWithItsCode() throws Exception {
    given(asset.changeSensorStatus(any()))
        .willThrow(
            new DownstreamCallException(
                "asset-service",
                Status.Code.FAILED_PRECONDITION,
                "CALIBRATION_EVIDENCE_REQUIRED",
                "evidence",
                null));

    mockMvc
        .perform(
            post("/api/v1/sensors/" + ID + "/status")
                .contentType("application/json")
                .content("{\"targetStatus\":\"ACTIVE\",\"reason\":\"back\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CALIBRATION_EVIDENCE_REQUIRED"));
  }

  @Test
  void aVersionConflictIsAConflictToReloadAndRetry() throws Exception {
    fails(Status.Code.ABORTED, "CONCURRENT_MODIFICATION", "Asset was modified concurrently: x");

    mockMvc
        .perform(get("/api/v1/sensors/" + ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reload")));
  }

  @Test
  void aSlowOrDownServiceIs504Or503WithoutExposingWhatItSaid() throws Exception {
    fails(Status.Code.DEADLINE_EXCEEDED, null, "deadline exceeded after 5.000s");
    mockMvc
        .perform(get("/api/v1/sensors/" + ID))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"));

    fails(Status.Code.UNAVAILABLE, null, "io exception: connection refused asset-service:9091");
    mockMvc
        .perform(get("/api/v1/sensors/" + ID))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9091"))));
  }

  @Test
  void anInternalErrorNeverLeaksItsDescription() throws Exception {
    fails(
        Status.Code.INTERNAL,
        null,
        "ERROR: relation \"asset.sensor\" does not exist at jdbc:postgresql://db");

    var result =
        mockMvc
            .perform(get("/api/v1/sensors/" + ID))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("does not exist")
        .doesNotContain("asset.sensor")
        .doesNotContain("jdbc")
        .doesNotContain("postgresql");
  }

  @Test
  void theProblemCarriesTheSameCorrelationIdAsTheResponseHeader() throws Exception {
    fails(Status.Code.NOT_FOUND, "SENSOR_NOT_FOUND", "x");

    mockMvc
        .perform(get("/api/v1/sensors/" + ID).header("X-Correlation-Id", "corr-asset-1"))
        .andExpect(header().string("X-Correlation-Id", "corr-asset-1"))
        .andExpect(jsonPath("$.correlationId").value("corr-asset-1"));
  }

  @Test
  void validationErrorsNameTheFieldAndNeverEchoTheValue() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sensors/" + ID + "/retirement")
                .contentType("application/json")
                .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("reason"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("xxxxxxxxxx"))));
  }
}
