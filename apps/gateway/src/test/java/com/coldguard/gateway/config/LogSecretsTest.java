package com.coldguard.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.infrastructure.InvalidCredentialsException;
import com.coldguard.gateway.testsupport.TestJwtKeys;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs login (success and failure) and authenticated requests with the most verbose security
 * logging and asserts that neither the password, the issued token nor the {@code Authorization}
 * header value reaches the log output (RNF-008).
 */
@SpringBootTest(
    properties = {
      "logging.level.org.springframework.security=TRACE",
      "logging.level.org.springframework.web=DEBUG",
      "logging.level.com.coldguard=DEBUG"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogSecretsTest {

  private static final TestJwtKeys KEYS = TestJwtKeys.generate();
  private static final String PASSWORD = "s3cret-Passw0rd-marker";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @MockitoBean private IdentityGrpcClient identityGrpcClient;

  @Test
  void loginAndAuthenticatedRequests_neverLogPasswordOrToken(CapturedOutput output)
      throws Exception {
    given(identityGrpcClient.verifyCredentials("marta", PASSWORD))
        .willReturn(
            new IdentityGrpcClient.VerifiedUser("u-1", "marta", List.of("MAINTENANCE_TECHNICIAN")));
    given(identityGrpcClient.verifyCredentials("marta", "wrong-" + PASSWORD))
        .willThrow(new InvalidCredentialsException());

    given(incidentGrpcClient.closeIncident(any()))
        .willReturn(
            CloseIncidentResponse.newBuilder()
                .setIncidentId("i1")
                .setStatus(IncidentStatus.CLOSED)
                .build());

    String body = login("marta", PASSWORD);
    login("marta", "wrong-" + PASSWORD);
    String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    assertThat(token).startsWith("eyJ");

    mockMvc.perform(
        post("/api/v1/incidents/i1/close")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content("{\"cause\":\"c\",\"resolutionComment\":\"r\"}"));
    mockMvc.perform(
        post("/api/v1/incidents/i1/close")
            .header("Authorization", "Bearer " + token + "tampered")
            .contentType("application/json")
            .content("{}"));

    assertThat(output.getAll())
        .as("security logging must be captured, or this test proves nothing")
        .contains("Securing POST /api/v1/incidents/i1/close")
        .doesNotContain(PASSWORD)
        .doesNotContain(token)
        .doesNotContain("Bearer eyJ");
  }

  private String login(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
