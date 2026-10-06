package com.coldguard.gateway.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.config.SecurityConfig;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IdentityServiceException;
import com.coldguard.gateway.infrastructure.InvalidCredentialsException;
import com.coldguard.gateway.infrastructure.JwtTokenIssuer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  private static final String LOGIN = "/api/v1/auth/login";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IdentityGrpcClient identity;
  @MockitoBean private JwtTokenIssuer tokens;
  @MockitoBean private JwtDecoder jwtDecoder;

  private static String body(String username, String password) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
  }

  @Test
  void validCredentialsReturnABearerToken() throws Exception {
    given(identity.verifyCredentials("marta", "secret-pw"))
        .willReturn(new IdentityGrpcClient.VerifiedUser("u-1", "marta", List.of("AUDITOR")));
    given(tokens.issue("u-1", "marta", List.of("AUDITOR")))
        .willReturn(new JwtTokenIssuer.IssuedToken("signed.jwt.value", Duration.ofHours(1)));

    mockMvc
        .perform(post(LOGIN).contentType("application/json").content(body("marta", "secret-pw")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("signed.jwt.value"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(3600));
  }

  @Test
  void rejectedCredentialsAreAGenericProblem401() throws Exception {
    given(identity.verifyCredentials("marta", "wrong"))
        .willThrow(new InvalidCredentialsException());

    mockMvc
        .perform(post(LOGIN).contentType("application/json").content(body("marta", "wrong")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("Invalid credentials"));
  }

  @Test
  void blankOversizedOrMalformedInputGetsTheSame401WithoutCallingIdentity() throws Exception {
    String expected = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401";
    mockMvc
        .perform(post(LOGIN).contentType("application/json").content(body("", "x")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mockMvc
        .perform(
            post(LOGIN).contentType("application/json").content(body("marta", "x".repeat(500))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mockMvc
        .perform(post(LOGIN).contentType("application/json").content("{not json"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mockMvc
        .perform(post(LOGIN).contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_CREDENTIALS")));
    org.mockito.Mockito.verifyNoInteractions(identity);
  }

  @Test
  void identityOutageIsABadGatewayThatDoesNotLeakDetails() throws Exception {
    given(identity.verifyCredentials("marta", "pw"))
        .willThrow(new IdentityServiceException("boom", new RuntimeException("host:9093")));

    mockMvc
        .perform(post(LOGIN).contentType("application/json").content(body("marta", "pw")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("IDENTITY_SERVICE_UNAVAILABLE"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9093"))));
  }
}
