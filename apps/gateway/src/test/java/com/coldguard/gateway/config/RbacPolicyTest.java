package com.coldguard.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.testsupport.TestJwtKeys;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Walks the role-to-endpoint table of {@code docs/security/authn-authz.md} against the real
 * security filter chain: an allowed role is never rejected by security (401/403), any other role
 * gets 403, no token gets 401, and a route that is not listed is denied.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacPolicyTest {

  private static final TestJwtKeys KEYS = TestJwtKeys.generate();
  private static final String API = "/api/v1";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
    registry.add("coldguard.gateway.technical-endpoints.enabled", () -> "true");
    registry.add("coldguard.gateway.cors.allowed-origins", () -> "http://localhost:5173");
    // Asset routes are real controllers now; point their channel at a closed port with a short
    // deadline so an allowed role gets a fast 503 (never 401/403) without needing a service.
    registry.add("spring.grpc.client.channels.asset-service.address", () -> "static://localhost:1");
    registry.add("coldguard.gateway.downstream.asset-service.deadline", () -> "500ms");
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentGrpcClient incidentGrpcClient;

  @MockitoBean private IdentityGrpcClient identityGrpcClient;

  private static final Role SUPERVISOR = Role.OPERATIONS_SUPERVISOR;
  private static final Role OPERATOR = Role.OPERATOR;
  private static final Role TECHNICIAN = Role.MAINTENANCE_TECHNICIAN;
  private static final Role AUDITOR = Role.AUDITOR;
  private static final Role ADMIN = Role.PLATFORM_ADMIN;

  static Stream<Arguments> routes() {
    return Stream.of(
        route(HttpMethod.GET, "/organizations", ADMIN, SUPERVISOR),
        route(HttpMethod.POST, "/organizations", ADMIN),
        route(HttpMethod.PUT, "/organizations/o1", ADMIN),
        route(HttpMethod.GET, "/sites", ADMIN, SUPERVISOR),
        route(HttpMethod.POST, "/sites", ADMIN),
        route(HttpMethod.PATCH, "/sites/s1", ADMIN),
        route(HttpMethod.GET, "/assets", ADMIN, SUPERVISOR),
        route(HttpMethod.POST, "/assets", ADMIN),
        route(HttpMethod.PUT, "/assets/a1", ADMIN),
        route(HttpMethod.GET, "/sensors", ADMIN, SUPERVISOR),
        route(HttpMethod.POST, "/sensors", ADMIN),
        route(HttpMethod.PUT, "/sensors/s1/profile", ADMIN),
        route(HttpMethod.POST, "/sensors/s1/status", ADMIN),
        route(HttpMethod.POST, "/sensors/s1/calibrations", ADMIN),
        route(HttpMethod.POST, "/sensors/s1/reassignment", ADMIN),
        route(HttpMethod.POST, "/sensors/s1/retirement", ADMIN),
        route(HttpMethod.GET, "/sensors/s1/history", ADMIN),
        route(HttpMethod.GET, "/sensors/s1/readings", ADMIN, SUPERVISOR),
        route(HttpMethod.GET, "/sensors/connectivity", ADMIN, SUPERVISOR),
        route(HttpMethod.POST, "/telemetry/test-readings", ADMIN),
        route(HttpMethod.GET, "/incidents", SUPERVISOR, OPERATOR, TECHNICIAN),
        route(HttpMethod.GET, "/incidents/i1", SUPERVISOR, OPERATOR, TECHNICIAN),
        route(HttpMethod.POST, "/incidents", ADMIN),
        route(HttpMethod.POST, "/incidents/i1/acknowledgement", SUPERVISOR),
        route(HttpMethod.POST, "/incidents/i1/escalation", SUPERVISOR),
        route(HttpMethod.POST, "/incidents/i1/close", TECHNICIAN),
        route(HttpMethod.GET, "/metrics/incidents", SUPERVISOR),
        route(HttpMethod.GET, "/audit-records", AUDITOR),
        route(HttpMethod.GET, "/users", ADMIN),
        route(HttpMethod.POST, "/users", ADMIN),
        route(HttpMethod.POST, "/users/u1/roles", ADMIN),
        route(HttpMethod.DELETE, "/users/u1/roles", ADMIN),
        route(HttpMethod.POST, "/users/u1/enabled", ADMIN));
  }

  private static Arguments route(HttpMethod method, String path, Role... allowed) {
    return Arguments.of(method, API + path, EnumSet.copyOf(Set.of(allowed)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("routes")
  void route_allowedRoleIsNotRejected_otherRolesAreForbidden_noTokenIsUnauthorized(
      HttpMethod method, String path, Set<Role> allowed) throws Exception {
    for (Role role : Role.values()) {
      int status = statusFor(method, path, role);
      if (allowed.contains(role)) {
        assertThat(status).as("%s %s as %s", method, path, role).isNotIn(401, 403);
      } else {
        assertThat(status).as("%s %s as %s", method, path, role).isEqualTo(403);
      }
    }
    assertThat(mockMvc.perform(request(method, path)).andReturn().getResponse().getStatus())
        .as("%s %s without token", method, path)
        .isEqualTo(401);
  }

  @org.junit.jupiter.api.BeforeEach
  void stubUserListing() {
    given(
            identityGrpcClient.listUsers(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .willReturn(new IdentityGrpcClient.UserPage(List.of(), 0, 20, 0, 0));
  }

  private int statusFor(HttpMethod method, String path, Role role) throws Exception {
    return mockMvc
        .perform(
            request(method, path)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role.name()))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("unlistedRoutes")
  void unlistedRoute_isDeniedEvenToAdmin(HttpMethod method, String path) throws Exception {
    assertThat(statusFor(method, path, ADMIN)).as("%s %s as admin", method, path).isEqualTo(403);
    assertThat(mockMvc.perform(request(method, path)).andReturn().getResponse().getStatus())
        .isEqualTo(401);
  }

  static Stream<Arguments> unlistedRoutes() {
    return Stream.of(
        Arguments.of(HttpMethod.GET, API + "/unknown"),
        Arguments.of(HttpMethod.DELETE, API + "/incidents/i1"),
        Arguments.of(HttpMethod.PUT, API + "/incidents/i1/close"),
        Arguments.of(HttpMethod.GET, API + "/auth/login"),
        Arguments.of(HttpMethod.GET, "/actuator/health"),
        Arguments.of(HttpMethod.GET, "/"));
  }

  @Test
  void login_isPublic() throws Exception {
    given(identityGrpcClient.verifyCredentials("u", "p"))
        .willReturn(new IdentityGrpcClient.VerifiedUser("u-1", "u", List.of("AUDITOR")));

    int status =
        mockMvc
            .perform(
                request(HttpMethod.POST, API + "/auth/login")
                    .contentType("application/json")
                    .content("{\"username\":\"u\",\"password\":\"p\"}"))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(status).isEqualTo(200);
  }

  @Test
  void preflight_fromAllowedOrigin_isAnsweredWithoutToken() throws Exception {
    var response =
        mockMvc
            .perform(
                options(API + "/incidents/i1/close")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Access-Control-Allow-Origin"))
        .isEqualTo("http://localhost:5173");
    assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
  }

  @Test
  void preflight_fromOtherOrigin_isRejected() throws Exception {
    var response =
        mockMvc
            .perform(
                options(API + "/incidents/i1/close")
                    .header("Origin", "http://evil.example")
                    .header("Access-Control-Request-Method", "POST"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
  }
}
