package com.coldguard.gateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.config.JwtConfig;
import com.coldguard.gateway.config.SecurityConfig;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.testsupport.TestJwtKeys;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real {@code JwtDecoder} end to end (real RS256 signature, issuer and expiry
 * validation against a throwaway key pair, {@link TestJwtKeys}) via a real {@code Authorization:
 * Bearer <token>} header. Complements, and does not replace, {@link IncidentControllerCloseTest} —
 * that class uses {@code SecurityMockMvcRequestPostProcessors.jwt()}, which injects an
 * already-built {@code Authentication} directly into the security context and never invokes a
 * decoder.
 *
 * <p>The keys are created in a static field because {@code @DynamicPropertySource} static methods
 * run before user-declared {@code @BeforeAll} methods.
 */
@WebMvcTest(controllers = IncidentController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class IncidentControllerCloseRealJwtTest {

  private static final String CLOSE_REQUEST_BODY =
      """
            { "cause": "overheating", "resolutionComment": "replaced sensor" }
            """;

  private static final String ISSUER = "coldguard-test";
  private static final TestJwtKeys KEYS = TestJwtKeys.generate();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.issuer", () -> ISSUER);
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
  }

  @Test
  void closeIncident_validTokenCorrectRole_returns200() throws Exception {
    given(incidentGrpcClient.closeIncident(any()))
        .willReturn(
            CloseIncidentResponse.newBuilder()
                .setIncidentId("incident-1")
                .setStatus(IncidentStatus.CLOSED)
                .setClosedAt("2026-01-01T00:00:00Z")
                .build());
    String token =
        KEYS.token(
            ISSUER,
            "tech-1",
            List.of("MAINTENANCE_TECHNICIAN"),
            Instant.now().plus(5, ChronoUnit.MINUTES));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value("incident-1"))
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  @Test
  void closeIncident_validTokenWrongRole_returns403() throws Exception {
    String token =
        KEYS.token(
            ISSUER,
            "supervisor-1",
            List.of("OPERATIONS_SUPERVISOR"),
            Instant.now().plus(5, ChronoUnit.MINUTES));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void closeIncident_invalidIssuer_returns401() throws Exception {
    String token =
        KEYS.token(
            "someone-else",
            "tech-1",
            List.of("MAINTENANCE_TECHNICIAN"),
            Instant.now().plus(5, ChronoUnit.MINUTES));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void closeIncident_invalidSignature_returns401() throws Exception {
    String token =
        TestJwtKeys.tokenSignedWithAnotherKey(
            ISSUER,
            "tech-1",
            List.of("MAINTENANCE_TECHNICIAN"),
            Instant.now().plus(5, ChronoUnit.MINUTES));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void closeIncident_expiredToken_returns401() throws Exception {
    String token =
        KEYS.token(
            ISSUER,
            "tech-1",
            List.of("MAINTENANCE_TECHNICIAN"),
            Instant.now().minus(5, ChronoUnit.MINUTES));

    mockMvc
        .perform(
            post("/api/v1/incidents/incident-1/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(CLOSE_REQUEST_BODY))
        .andExpect(status().isUnauthorized());
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
}
