package com.coldguard.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.gateway.config.JwtConfig;
import com.coldguard.gateway.config.JwtProperties;
import com.coldguard.gateway.testsupport.TestJwtKeys;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Issues a token with the real encoder and reads it back with the real decoder. */
class JwtTokenIssuerTest {

  private static final Instant NOW = Instant.now();

  private final TestJwtKeys keys = TestJwtKeys.generate();
  private final JwtProperties props =
      new JwtProperties(
          "coldguard-test",
          keys.privateKeyFile(),
          keys.publicKeyFile(),
          Duration.ofMinutes(10),
          Duration.ofSeconds(5));
  private final JwtConfig config = new JwtConfig();

  private JwtTokenIssuer issuerAt(Instant now) {
    return new JwtTokenIssuer(config.jwtEncoder(props), props, Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void issuedTokenCarriesTheDocumentedClaimsAndValidates() {
    var issued = issuerAt(NOW).issue("u-1", "marta", List.of("AUDITOR", "OPERATOR"));

    Jwt jwt = config.jwtDecoder(props).decode(issued.value());

    assertThat(jwt.getClaimAsString("iss")).isEqualTo("coldguard-test");
    assertThat(jwt.getSubject()).isEqualTo("u-1");
    assertThat(jwt.<String>getClaim("preferred_username")).isEqualTo("marta");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("AUDITOR", "OPERATOR");
    assertThat(jwt.getId()).isNotBlank();
    assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    assertThat(issued.expiresIn()).isEqualTo(Duration.ofMinutes(10));
    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
  }

  @Test
  void tokenIsRejectedOnceExpiredBeyondTheClockSkew() {
    var issued =
        issuerAt(NOW.minus(Duration.ofMinutes(20))).issue("u-1", "marta", List.of("AUDITOR"));

    assertThatThrownBy(() -> config.jwtDecoder(props).decode(issued.value()))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void decoderRejectsATokenFromAnotherIssuerOrKey() {
    JwtDecoder decoder = config.jwtDecoder(props);

    assertThatThrownBy(
            () ->
                decoder.decode(
                    keys.token("other", "u", List.of("AUDITOR"), NOW.plus(Duration.ofMinutes(5)))))
        .isInstanceOf(BadJwtException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    TestJwtKeys.tokenSignedWithAnotherKey(
                        "coldguard-test",
                        "u",
                        List.of("AUDITOR"),
                        NOW.plus(Duration.ofMinutes(5)))))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void missingKeyFilesStopStartup() {
    var missing =
        new JwtProperties(
            "coldguard-test",
            Path.of("/nonexistent/private.pem"),
            Path.of("/nonexistent/public.pem"),
            Duration.ofMinutes(10),
            Duration.ofSeconds(5));

    assertThatThrownBy(() -> config.jwtDecoder(missing)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> config.jwtEncoder(missing)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void propertiesWithoutIssuerOrKeyPathsAreRefused() {
    assertThatThrownBy(
            () ->
                new JwtProperties(
                    "",
                    keys.privateKeyFile(),
                    keys.publicKeyFile(),
                    Duration.ofMinutes(1),
                    Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new JwtProperties(
                    "x", Path.of(""), keys.publicKeyFile(), Duration.ofMinutes(1), Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> new JwtProperties("x", null, null, Duration.ofMinutes(1), Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }
}
