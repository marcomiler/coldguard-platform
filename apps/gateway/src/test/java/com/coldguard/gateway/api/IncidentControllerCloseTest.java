package com.coldguard.gateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.config.SecurityConfig;
import com.coldguard.gateway.infrastructure.IncidentAlreadyClosedException;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentNotFoundException;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unlike IncidentControllerTest (filters disabled), this class runs with the real
 * SecurityFilterChain (SecurityConfig) to exercise authentication/authorization for the close
 * endpoint (HU-026, RN-019).
 */
@WebMvcTest(controllers = IncidentController.class)
@Import(SecurityConfig.class)
class IncidentControllerCloseTest {

  private static final String CLOSE_REQUEST_BODY =
      """
            { "cause": "overheating", "resolutionComment": "replaced sensor" }
            """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @Test
  void closeIncident_authorizedActor_returns200() throws Exception {
    given(incidentGrpcClient.closeIncident(any()))
        .willReturn(
            CloseIncidentResponse.newBuilder()
                .setIncidentId("incident-1")
                .setStatus(IncidentStatus.CLOSED)
                .setClosedAt("2026-01-01T00:00:00Z")
                .build());

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MAINTENANCE_TECHNICIAN")))
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value("incident-1"))
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  @Test
  void closeIncident_noToken_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void closeIncident_wrongRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void closeIncident_incidentNotFound_returns404() throws Exception {
    given(incidentGrpcClient.closeIncident(any()))
        .willThrow(new IncidentNotFoundException("Incident not found: incident-1", null));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MAINTENANCE_TECHNICIAN")))
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
  }

  @Test
  void closeIncident_alreadyClosed_returns409() throws Exception {
    given(incidentGrpcClient.closeIncident(any()))
        .willThrow(
            new IncidentAlreadyClosedException("Incident is already closed: incident-1", null));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MAINTENANCE_TECHNICIAN")))
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_CLOSED"));
  }
}
