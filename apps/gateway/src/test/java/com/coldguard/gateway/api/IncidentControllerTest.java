package com.coldguard.gateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.infrastructure.IncidentAlreadyExistsException;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentServiceException;
import com.coldguard.gateway.infrastructure.InvalidIncidentRequestException;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import com.coldguard.incident.grpc.v1.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = IncidentController.class)
@AutoConfigureMockMvc(addFilters = false)
class IncidentControllerTest {

  private static final String VALID_REQUEST_BODY =
      """
            {
              "assetId": "asset-1",
              "assetCriticality": "CRITICALITY_HIGH",
              "sensorId": "sensor-1",
              "anomalyType": "high-temperature",
              "magnitude": "MAGNITUDE_HIGH",
              "persistent": false,
              "correlationId": "corr-1"
            }
            """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @Test
  void createIncident_success_returns201WithBody() throws Exception {
    CreateIncidentResponse grpcResponse =
        CreateIncidentResponse.newBuilder()
            .setIncidentId("incident-1")
            .setStatus(IncidentStatus.CREATED)
            .setPriority(Priority.P1)
            .build();
    given(incidentGrpcClient.createIncident(any())).willReturn(grpcResponse);

    mockMvc
        .perform(
            post("/api/v1/incidents").contentType("application/json").content(VALID_REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId").value("incident-1"))
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.priority").value("P1"));
  }

  @Test
  void createIncident_alreadyExists_returns409() throws Exception {
    given(incidentGrpcClient.createIncident(any()))
        .willThrow(new IncidentAlreadyExistsException("duplicate incident", null));

    mockMvc
        .perform(
            post("/api/v1/incidents").contentType("application/json").content(VALID_REQUEST_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_EXISTS"));
  }

  @Test
  void createIncident_invalidRequest_returns400() throws Exception {
    given(incidentGrpcClient.createIncident(any()))
        .willThrow(new InvalidIncidentRequestException("magnitude is required", null));

    mockMvc
        .perform(
            post("/api/v1/incidents").contentType("application/json").content(VALID_REQUEST_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INCIDENT_REQUEST"));
  }

  @Test
  void createIncident_missingAssetCriticality_returns400WithoutCallingClient() throws Exception {
    String bodyMissingCriticality =
        """
                {
                  "assetId": "asset-1",
                  "sensorId": "sensor-1",
                  "anomalyType": "high-temperature",
                  "magnitude": "MAGNITUDE_HIGH",
                  "persistent": false,
                  "correlationId": "corr-1"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType("application/json")
                .content(bodyMissingCriticality))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INCIDENT_REQUEST"))
        .andExpect(jsonPath("$.detail").value("assetCriticality is required"));
    verifyNoInteractions(incidentGrpcClient);
  }

  @Test
  void createIncident_missingMagnitude_returns400WithoutCallingClient() throws Exception {
    String bodyMissingMagnitude =
        """
                {
                  "assetId": "asset-1",
                  "assetCriticality": "CRITICALITY_HIGH",
                  "sensorId": "sensor-1",
                  "anomalyType": "high-temperature",
                  "persistent": false,
                  "correlationId": "corr-1"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/incidents").contentType("application/json").content(bodyMissingMagnitude))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INCIDENT_REQUEST"))
        .andExpect(jsonPath("$.detail").value("magnitude is required"));
    verifyNoInteractions(incidentGrpcClient);
  }

  @Test
  void createIncident_serviceFailure_returns502() throws Exception {
    given(incidentGrpcClient.createIncident(any()))
        .willThrow(new IncidentServiceException("unexpected error", null));

    mockMvc
        .perform(
            post("/api/v1/incidents").contentType("application/json").content(VALID_REQUEST_BODY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("INCIDENT_SERVICE_UNAVAILABLE"));
  }
}
