package com.coldguard.incident.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class VerifyCredentialsServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final String PASSWORD = "correct-horse-battery";

  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final InMemoryUserAccounts users = new InMemoryUserAccounts();
  private MutableClock clock;
  private VerifyCredentialsService service;

  /** A clock the test can move forward. */
  private static final class MutableClock extends Clock {
    Instant now = NOW;

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void setUp() {
    clock = new MutableClock();
    service =
        new VerifyCredentialsService(
            users, encoder, new LockoutPolicy(3, Duration.ofMinutes(15)), clock);
    users.insert(
        UserAccount.register(
            "marta",
            "marta@example.com",
            "Marta",
            encoder.encode(PASSWORD),
            Set.of(Role.OPERATOR, Role.AUDITOR),
            NOW),
        "test");
  }

  @Test
  void validCredentialsAuthenticateWithTheirRoles() {
    var result = service.verify("  MARTA ", PASSWORD);

    assertThat(result).isInstanceOf(AuthenticationResult.Authenticated.class);
    var user = ((AuthenticationResult.Authenticated) result).user();
    assertThat(user.username()).isEqualTo("marta");
    assertThat(user.roles()).containsExactlyInAnyOrder(Role.OPERATOR, Role.AUDITOR);
  }

  @Test
  void wrongPasswordAndUnknownUserAreIndistinguishable() {
    assertThat(service.verify("marta", "nope")).isEqualTo(new AuthenticationResult.Rejected());
    assertThat(service.verify("ghost", PASSWORD)).isEqualTo(new AuthenticationResult.Rejected());
    assertThat(service.verify(null, null)).isEqualTo(new AuthenticationResult.Rejected());
  }

  @Test
  void oversizedPasswordIsRejectedWithoutALookup() {
    assertThat(service.verify("marta", "x".repeat(500)))
        .isEqualTo(new AuthenticationResult.Rejected());
    assertThat(users.byUsername.get("marta").failedAttempts()).isZero();
  }

  @Test
  void locksAfterMaxFailuresEvenForTheCorrectPassword() {
    for (int i = 0; i < 3; i++) {
      service.verify("marta", "wrong");
    }

    assertThat(service.verify("marta", PASSWORD)).isInstanceOf(AuthenticationResult.Rejected.class);

    clock.now = NOW.plus(Duration.ofMinutes(16));
    assertThat(service.verify("marta", PASSWORD))
        .isInstanceOf(AuthenticationResult.Authenticated.class);
  }

  @Test
  void attemptsWhileLockedDoNotExtendTheLock() {
    for (int i = 0; i < 3; i++) {
      service.verify("marta", "wrong");
    }
    Instant lockedUntil = users.byUsername.get("marta").lockedUntil();

    clock.now = NOW.plusSeconds(60);
    service.verify("marta", "wrong");

    assertThat(users.byUsername.get("marta").lockedUntil()).isEqualTo(lockedUntil);
  }

  @Test
  void successResetsTheFailureCounter() {
    service.verify("marta", "wrong");
    service.verify("marta", "wrong");

    service.verify("marta", PASSWORD);

    assertThat(users.byUsername.get("marta").failedAttempts()).isZero();
  }
}
