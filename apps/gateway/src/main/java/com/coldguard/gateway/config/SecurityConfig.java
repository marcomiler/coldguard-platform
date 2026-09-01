package com.coldguard.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Only {@code POST /api/v1/incidents/{id}/close} is protected (HU-026, RN-019). Every other
 * request, including {@code POST /api/v1/incidents}, remains {@code permitAll()} — real edge
 * authentication for the rest of the API is out of scope until Sprint 4 resolves R-014.
 *
 * <p>The JWT issuer is not decided yet: no value is hardcoded or defaulted here. Setting
 * {@code app.security.jwt.issuer-uri} (e.g. via the APP_SECURITY_JWT_ISSUER_URI env var) once a
 * provider is approved activates real validation via {@link #jwtDecoder}. Until then,
 * {@link #pendingJwtDecoder} makes every real token fail closed (401) — the close endpoint is
 * currently unreachable from outside the process, by design, not merely unfinished.
 *
 * <p><b>Test coverage gap, by design of this slice</b>: tests exercising this filter chain use
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}, which injects an already-built
 * {@code Authentication} directly into the security context and never invokes {@link JwtDecoder}
 * or the real bearer-token filter. Passing those tests does not prove that a real
 * {@code Authorization: Bearer <token>} header would be validated or rejected correctly — that
 * requires a real (or test) issuer/JWKS, which is not configured yet.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CLOSE_INCIDENT_PATH = "/api/v1/incidents/*/close";
    private static final String MAINTENANCE_TECHNICIAN_AUTHORITY = "ROLE_MAINTENANCE_TECHNICIAN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, CLOSE_INCIDENT_PATH).hasAuthority(MAINTENANCE_TECHNICIAN_AUTHORITY)
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
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

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt", name = "issuer-uri")
    JwtDecoder jwtDecoder(@Value("${app.security.jwt.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * Active only while {@code app.security.jwt.issuer-uri} is unset (no provider approved yet).
     * Every decode attempt fails, so any real bearer token is rejected with 401 — the close
     * endpoint stays inaccessible from outside the process rather than silently open.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder pendingJwtDecoder() {
        return token -> {
            throw new BadJwtException("JWT issuer not configured yet (app.security.jwt.issuer-uri)");
        };
    }
}
