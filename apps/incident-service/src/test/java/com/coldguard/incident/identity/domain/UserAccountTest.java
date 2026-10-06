package com.coldguard.incident.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAccountTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final Duration LOCK = Duration.ofMinutes(15);

  private UserAccount user(Set<Role> roles) {
    return UserAccount.register(
        "  Marta ", "Marta@Example.com", "Marta", "{bcrypt}hash", roles, NOW);
  }

  @Test
  void normalizesUsernameAndEmail() {
    UserAccount user = user(Set.of(Role.AUDITOR));

    assertThat(user.username()).isEqualTo("marta");
    assertThat(user.email()).isEqualTo("marta@example.com");
    assertThat(user.enabled()).isTrue();
  }

  @Test
  void requiresAtLeastOneRole() {
    assertThatThrownBy(() -> user(Set.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankUsernameIsRejected() {
    assertThatThrownBy(
            () -> UserAccount.register(" ", "a@b.c", "A", "{bcrypt}h", Set.of(Role.OPERATOR), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void locksAfterMaxAttemptsAndBlocksAuthentication() {
    UserAccount user = user(Set.of(Role.OPERATOR));

    UserAccount afterTwo =
        user.registerFailedAttempt(NOW, 3, LOCK).registerFailedAttempt(NOW, 3, LOCK);
    assertThat(afterTwo.isLocked(NOW)).isFalse();

    UserAccount locked = afterTwo.registerFailedAttempt(NOW, 3, LOCK);
    assertThat(locked.failedAttempts()).isEqualTo(3);
    assertThat(locked.canAuthenticate(NOW.plusSeconds(1))).isFalse();
    assertThat(locked.canAuthenticate(NOW.plus(LOCK).plusSeconds(1))).isTrue();
  }

  @Test
  void expiredLockStartsTheCountOver() {
    UserAccount locked =
        user(Set.of(Role.OPERATOR)).registerFailedAttempt(NOW, 1, LOCK); // locked right away
    Instant later = NOW.plus(LOCK).plusSeconds(1);

    UserAccount next = locked.registerFailedAttempt(later, 3, LOCK);

    assertThat(next.failedAttempts()).isEqualTo(1);
    assertThat(next.isLocked(later)).isFalse();
  }

  @Test
  void successfulLoginClearsCounterAndLock() {
    UserAccount user =
        user(Set.of(Role.OPERATOR))
            .registerFailedAttempt(NOW, 5, LOCK)
            .registerSuccessfulLogin(NOW);

    assertThat(user.failedAttempts()).isZero();
    assertThat(user.lockedUntil()).isNull();
  }

  @Test
  void disabledAccountNeverAuthenticates() {
    UserAccount user = user(Set.of(Role.OPERATOR));
    UserAccount disabled =
        new UserAccount(
            user.id(),
            user.username(),
            user.email(),
            user.displayName(),
            user.passwordHash(),
            false,
            0,
            null,
            user.roles(),
            NOW,
            NOW);

    assertThat(disabled.canAuthenticate(NOW)).isFalse();
  }
}
