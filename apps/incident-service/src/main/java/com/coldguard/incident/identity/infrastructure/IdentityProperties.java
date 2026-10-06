package com.coldguard.incident.identity.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("coldguard.identity")
public record IdentityProperties(
    @DefaultValue Lockout lockout,
    @DefaultValue Password password,
    @DefaultValue Bootstrap bootstrap) {

  public record Lockout(
      @DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration duration) {}

  public record Password(
      @DefaultValue("8") int minLength, @DefaultValue("10") int bcryptStrength) {}

  /** {@code demoPassword} is read from the environment and never has a default. */
  public record Bootstrap(@DefaultValue("false") boolean enabled, String demoPassword) {}
}
