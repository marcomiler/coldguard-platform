package com.coldguard.incident.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A person who can sign in. Never carries a clear-text password: only the hash produced by the
 * application's password encoder.
 */
public record UserAccount(
    UUID id,
    String username,
    String email,
    String displayName,
    String passwordHash,
    boolean enabled,
    int failedAttempts,
    Instant lockedUntil,
    Set<Role> roles,
    Instant createdAt,
    Instant updatedAt) {

  public UserAccount {
    username = normalizeUsername(username);
    email = requireNonBlank(email, "email").toLowerCase(Locale.ROOT);
    displayName = requireNonBlank(displayName, "displayName");
    passwordHash = requireNonBlank(passwordHash, "passwordHash");
    if (username.isEmpty()) {
      throw new IllegalArgumentException("username is required");
    }
    roles = Set.copyOf(roles);
  }

  public static UserAccount register(
      String username,
      String email,
      String displayName,
      String passwordHash,
      Set<Role> roles,
      Instant now) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("at least one role is required");
    }
    return new UserAccount(
        UUID.randomUUID(),
        username,
        email,
        displayName,
        passwordHash,
        true,
        0,
        null,
        roles,
        now,
        now);
  }

  public static String normalizeUsername(String username) {
    return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /** A disabled, locked or role-less account never authenticates. */
  public boolean canAuthenticate(Instant now) {
    return enabled && !isLocked(now) && !roles.isEmpty();
  }

  /**
   * Counts a wrong password and locks the account once {@code maxAttempts} is reached. A lock that
   * has already expired starts the count over.
   */
  public UserAccount registerFailedAttempt(Instant now, int maxAttempts, Duration lockDuration) {
    int previous = lockedUntil != null && !lockedUntil.isAfter(now) ? 0 : failedAttempts;
    int attempts = previous + 1;
    Instant lock = attempts >= maxAttempts ? now.plus(lockDuration) : null;
    return new UserAccount(
        id,
        username,
        email,
        displayName,
        passwordHash,
        enabled,
        attempts,
        lock,
        roles,
        createdAt,
        now);
  }

  public UserAccount registerSuccessfulLogin(Instant now) {
    return new UserAccount(
        id, username, email, displayName, passwordHash, enabled, 0, null, roles, createdAt, now);
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.strip();
  }
}
