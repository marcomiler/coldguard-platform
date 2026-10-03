package com.coldguard.gateway.api;

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
import com.coldguard.gateway.infrastructure.IncidentAlreadyExistsException;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentServiceException;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentStatus;
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

  private static final String BODY_WITHOUT_CORRELATION =
      """
            {"assetId":"a1","assetCriticality":"CRITICALITY_HIGH","sensorId":"s1",
             "anomalyType":"high-temperature","magnitude":"MAGNITUDE_HIGH","persistent":false}
            """;

  private IncidentGrpcClient grpcClient;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    grpcClient = mock(IncidentGrpcClient.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new IncidentController(grpcClient))
            .setControllerAdvice(new IncidentControllerAdvice())
            .addFilters(new CorrelationIdFilter())
            .build();
  }

  @Test
  void conflict_isProblemJsonWithCodeAndCallerCorrelationId() throws Exception {
    given(grpcClient.createIncident(any()))
        .willThrow(new IncidentAlreadyExistsException("already open", null));

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-dup-1")
                .content(BODY_WITHOUT_CORRELATION))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("X-Correlation-Id", "corr-dup-1"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.detail").value("already open"))
        .andExpect(jsonPath("$.correlationId").value("corr-dup-1"));
  }

  @Test
  void upstreamFailure_returns502WithGeneratedCorrelationIdInBodyAndHeader() throws Exception {
    given(grpcClient.createIncident(any())).willThrow(new IncidentServiceException("down", null));

    String headerValue =
        mockMvc
            .perform(
                post("/api/v1/incidents")
                    .contentType("application/json")
                    .content(BODY_WITHOUT_CORRELATION))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("INCIDENT_SERVICE_UNAVAILABLE"))
            .andReturn()
            .getResponse()
            .getHeader("X-Correlation-Id");

    assertThat(headerValue).isNotBlank();
    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", headerValue)
                .content(BODY_WITHOUT_CORRELATION))
        .andExpect(jsonPath("$.correlationId").value(headerValue));
  }

  @Test
  void bodyWithoutCorrelationId_usesHeaderValueForGrpcRequest() throws Exception {
    given(grpcClient.createIncident(any()))
        .willReturn(
            CreateIncidentResponse.newBuilder()
                .setIncidentId("i1")
                .setStatus(IncidentStatus.CREATED)
                .build());

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-from-header")
                .content(BODY_WITHOUT_CORRELATION))
        .andExpect(status().isCreated());

    ArgumentCaptor<CreateIncidentRequest> captor =
        ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(grpcClient).createIncident(captor.capture());
    assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-from-header");
  }

  @Test
  void bodyCorrelationId_winsOverHeaderForGrpcRequest() throws Exception {
    given(grpcClient.createIncident(any())).willReturn(CreateIncidentResponse.getDefaultInstance());

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-from-header")
                .content(
                    """
                                                {"assetId":"a1","assetCriticality":"CRITICALITY_HIGH","sensorId":"s1",
                                                 "anomalyType":"t","magnitude":"MAGNITUDE_HIGH","persistent":false,
                                                 "correlationId":"corr-from-body"}
                                                """))
        .andExpect(status().isCreated());

    ArgumentCaptor<CreateIncidentRequest> captor =
        ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(grpcClient).createIncident(captor.capture());
    assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-from-body");
  }

  @Test
  void malformedBody_returnsProblemJsonAndCorrelationId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .header("X-Correlation-Id", "corr-malformed-1")
                .content(
                    """
                                                {"assetId":"a1","assetCriticality":"CRITICALITY_HIGH","sensorId":"s1",
                                                 "anomalyType":"t","magnitude":"MAGNITUDE_HIGH","persistent":null}
                                                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("X-Correlation-Id", "corr-malformed-1"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
        .andExpect(jsonPath("$.correlationId").value("corr-malformed-1"));
  }
}
