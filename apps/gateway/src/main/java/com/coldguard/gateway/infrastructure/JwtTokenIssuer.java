package com.coldguard.gateway.infrastructure;

import com.coldguard.gateway.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Signs the access token (RS256) for a user whose credentials Identity has just verified. */
@Component
public class JwtTokenIssuer {

  public record IssuedToken(String value, Duration expiresIn) {}

  private final JwtEncoder encoder;
  private final JwtProperties props;
  private final Clock clock;

  public JwtTokenIssuer(JwtEncoder encoder, JwtProperties props, Clock clock) {
    this.encoder = encoder;
    this.props = props;
    this.clock = clock;
  }

  /** {@code roles} are the plain role names, without the {@code ROLE_} prefix. */
  public IssuedToken issue(String userId, String username, Collection<String> roles) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .subject(userId)
            .claim("preferred_username", username)
            .claim("roles", List.copyOf(roles))
            .issuedAt(now)
            .expiresAt(now.plus(props.ttl()))
            .id(UUID.randomUUID().toString())
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
            .getTokenValue();
    return new IssuedToken(token, props.ttl());
  }
}
