package com.coldguard.gateway.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Both key paths and the issuer are mandatory and have no default: the gateway must not start
 * without the keys it signs and validates with.
 */
@ConfigurationProperties("coldguard.security.jwt")
public record JwtProperties(
    String issuer,
    Path privateKeyPath,
    Path publicKeyPath,
    @DefaultValue("1h") Duration ttl,
    @DefaultValue("30s") Duration clockSkew) {

  public JwtProperties {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalStateException("coldguard.security.jwt.issuer is required");
    }
    if (isBlank(privateKeyPath) || isBlank(publicKeyPath)) {
      throw new IllegalStateException(
          "coldguard.security.jwt.private-key-path and public-key-path are required");
    }
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalStateException("coldguard.security.jwt.ttl must be positive");
    }
  }

  private static boolean isBlank(Path path) {
    return path == null || path.toString().isBlank();
  }
}
