package com.coldguard.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.testsupport.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With the default configuration the technical incident-creation route is closed to everyone. */
@SpringBootTest
@AutoConfigureMockMvc
class TechnicalEndpointsDisabledTest {

  private static final TestJwtKeys KEYS = TestJwtKeys.generate();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @MockitoBean private IdentityGrpcClient identityGrpcClient;

  @Test
  void createIncident_byAdmin_isForbiddenWhileTechnicalEndpointsAreDisabled() throws Exception {
    int status =
        mockMvc
            .perform(
                post("/api/v1/incidents")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(status).isEqualTo(403);
  }
}
