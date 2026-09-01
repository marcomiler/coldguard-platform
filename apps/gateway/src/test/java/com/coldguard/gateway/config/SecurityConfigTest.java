package com.coldguard.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test of the roles-claim-to-authority mapping, without a Spring context: builds a Jwt
 * directly and runs it through the converter bean's logic.
 */
class SecurityConfigTest {

    private final JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

    @Test
    void convert_rolesClaim_mapsToRoleAuthorities() {
        Jwt jwt = jwtWithRoles(List.of("MAINTENANCE_TECHNICIAN"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_MAINTENANCE_TECHNICIAN");
    }

    @Test
    void convert_missingRolesClaim_returnsNoRoleAuthority() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "actor-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    private static Jwt jwtWithRoles(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "actor-1")
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
