package com.coldguard.gateway.config;

import static com.coldguard.gateway.config.Role.AUDITOR;
import static com.coldguard.gateway.config.Role.MAINTENANCE_TECHNICIAN;
import static com.coldguard.gateway.config.Role.OPERATIONS_SUPERVISOR;
import static com.coldguard.gateway.config.Role.OPERATOR;
import static com.coldguard.gateway.config.Role.PLATFORM_ADMIN;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Deny-by-default RBAC: every route is declared here, in one readable table, and anything not
 * listed is rejected (401 without a valid token, 403 with one). Rules are evaluated in order, so a
 * specific route must come before the generic one it narrows. The role-to-endpoint table is
 * documented in {@code docs/security/authn-authz.md}; keep both in sync.
 *
 * <p>Tokens are validated by the {@code JwtDecoder} from {@link JwtConfig}, which needs the
 * configured public key at startup.
 *
 * <p>Tests that use {@code SecurityMockMvcRequestPostProcessors.jwt()} inject an already-built
 * {@code Authentication} and never run the decoder; the real decoder is covered by tests that send
 * an actual {@code Authorization: Bearer <token>} header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String API = "/api/v1";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Value("${coldguard.gateway.technical-endpoints.enabled:false}")
          boolean technicalEndpointsEnabled)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> declareRoutes(auth, technicalEndpointsEnabled))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  private static void declareRoutes(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
      boolean technicalEndpointsEnabled) {
    // Public.
    auth.requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll();
    // Actuator lives on the separate management port; this matcher never matches the public port.
    auth.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll();

    // Master data and sensors.
    allow(auth, HttpMethod.GET, List.of(API + "/sensors/*/history"), PLATFORM_ADMIN);
    allow(
        auth,
        HttpMethod.GET,
        resources("organizations", "sites", "assets", "sensors"),
        PLATFORM_ADMIN,
        OPERATIONS_SUPERVISOR);
    for (HttpMethod write : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
      allow(auth, write, resources("organizations", "sites", "assets", "sensors"), PLATFORM_ADMIN);
    }
    allow(auth, HttpMethod.POST, List.of(API + "/telemetry/test-readings"), PLATFORM_ADMIN);

    // Incidents.
    allow(
        auth,
        HttpMethod.GET,
        List.of(API + "/incidents", API + "/incidents/*"),
        OPERATIONS_SUPERVISOR,
        OPERATOR,
        MAINTENANCE_TECHNICIAN);
    if (technicalEndpointsEnabled) {
      allow(auth, HttpMethod.POST, List.of(API + "/incidents"), PLATFORM_ADMIN);
    }
    allow(
        auth,
        HttpMethod.POST,
        List.of(API + "/incidents/*/acknowledgement", API + "/incidents/*/escalation"),
        OPERATIONS_SUPERVISOR);
    allow(auth, HttpMethod.POST, List.of(API + "/incidents/*/close"), MAINTENANCE_TECHNICIAN);

    // Metrics, audit and user administration.
    allow(auth, HttpMethod.GET, List.of(API + "/metrics/incidents"), OPERATIONS_SUPERVISOR);
    allow(auth, HttpMethod.GET, resources("audit-records"), AUDITOR);
    for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE)) {
      allow(auth, method, resources("users"), PLATFORM_ADMIN);
    }

    auth.anyRequest().denyAll();
  }

  private static void allow(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
      HttpMethod method,
      List<String> patterns,
      Role... roles) {
    auth.requestMatchers(method, patterns.toArray(String[]::new))
        .hasAnyRole(Arrays.stream(roles).map(Role::name).toArray(String[]::new));
  }

  /** A collection route and everything under it, e.g. {@code /users} and {@code /users/**}. */
  private static List<String> resources(String... names) {
    return Arrays.stream(names)
        .flatMap(name -> java.util.stream.Stream.of(API + "/" + name, API + "/" + name + "/**"))
        .toList();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${coldguard.gateway.cors.allowed-origins:}") List<String> allowedOrigins) {
    if (allowedOrigins.contains("*")) {
      throw new IllegalStateException(
          "coldguard.gateway.cors.allowed-origins must list explicit origins, not '*'");
    }
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(allowedOrigins);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
    cors.setExposedHeaders(List.of("X-Correlation-Id"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration(API + "/**", cors);
    return source;
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles == null) {
            return List.of();
          }
          return roles.stream()
              .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .toList();
        });
    return converter;
  }
}
