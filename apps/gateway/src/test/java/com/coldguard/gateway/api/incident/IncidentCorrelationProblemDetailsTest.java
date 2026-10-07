package com.coldguard.gateway.api.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.commons.correlation.CorrelationIdFilter;
import com.coldguard.gateway.api.error.ApiExceptionHandler;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentView;
import io.grpc.Status;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Wires the real correlation filter, controller and advice together (no Spring context, no security
 * chain) to verify Problem Details output and correlation propagation to the gRPC call.
 */
class IncidentCorrelationProblemDetailsTest {

  private static final String BODY =
      """
      {"assetId":"a1","assetCriticality":"HIGH","sensorId":"s1",
       "anomalyType":"high-temperature","magnitude":"HIGH","persistent":false}
      """;

  private IncidentGrpcClient grpcClient;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    grpcClient = mock(IncidentGrpcClient.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new IncidentController(grpcClient))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
  }

  @Test
  void conflict_isProblemJsonWithCodeExistingIdAndCallerCorrelationId() throws Exception {
    given(grpcClient.createIncident(any()))
        .willThrow(
            new DownstreamCallException(
                "incident-service",
                Status.Code.ALREADY_EXISTS,
                "INCIDENT_ALREADY_EXISTS",
                "already open",
                Map.of("existingIncidentId", "inc-7"),
                null));

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-dup-1")
                .content(BODY))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("X-Correlation-Id", "corr-dup-1"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.existingIncidentId").value("inc-7"))
        .andExpect(jsonPath("$.correlationId").value("corr-dup-1"));
  }

  @Test
  void upstreamFailure_returns503WithGeneratedCorrelationIdInBodyAndHeader() throws Exception {
    given(grpcClient.createIncident(any()))
        .willThrow(
            new DownstreamCallException(
                "incident-service", Status.Code.UNAVAILABLE, null, "down", null));

    String headerValue =
        mockMvc
            .perform(post("/api/v1/incidents").contentType("application/json").content(BODY))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
            .andReturn()
            .getResponse()
            .getHeader("X-Correlation-Id");

    assertThat(headerValue).isNotBlank();
    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", headerValue)
                .content(BODY))
        .andExpect(jsonPath("$.correlationId").value(headerValue));
  }

  @Test
  void theCorrelationIdOfTheRequestTravelsInTheGrpcCall() throws Exception {
    given(grpcClient.createIncident(any()))
        .willReturn(
            CreateIncidentResponse.newBuilder()
                .setIncident(IncidentView.newBuilder().setIncidentId("i1"))
                .build());

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-xyz")
                .content(BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/incidents/i1"));

    ArgumentCaptor<CreateIncidentRequest> captor =
        ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(grpcClient).createIncident(captor.capture());
    assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-xyz");
  }

  @Test
  void malformedBody_returnsProblemJsonAndCorrelationId() throws Exception {
    mockMvc
        .perform(post("/api/v1/incidents").contentType("application/json").content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }
}
