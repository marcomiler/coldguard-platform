package com.coldguard.incident.identity.application;

import com.coldguard.incident.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a username and password (CU-016). Exactly one hash comparison runs on every path, against
 * a dummy hash when the user does not exist, so response time does not reveal whether an account
 * exists.
 */
@Service
public class VerifyCredentialsService {

  private static final AuthenticationResult REJECTED = new AuthenticationResult.Rejected();

  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final LockoutPolicy lockout;
  private final Clock clock;
  private final String dummyHash;

  public VerifyCredentialsService(
      UserAccountRepository users, PasswordEncoder encoder, LockoutPolicy lockout, Clock clock) {
    this.users = users;
    this.encoder = encoder;
    this.lockout = lockout;
    this.clock = clock;
    this.dummyHash = encoder.encode("coldguard-timing-equalizer");
  }

  @Transactional
  public AuthenticationResult verify(String username, String password) {
    String candidate = password == null ? "" : password;
    Optional<UserAccount> found =
        candidate.length() > PasswordPolicy.MAX_LENGTH
            ? Optional.empty()
            : users.findByUsernameForUpdate(UserAccount.normalizeUsername(username));

    boolean matches =
        encoder.matches(candidate, found.map(UserAccount::passwordHash).orElse(dummyHash));
    if (found.isEmpty()) {
      return REJECTED;
    }

    UserAccount user = found.get();
    Instant now = clock.instant();
    if (!user.canAuthenticate(now)) {
      return REJECTED;
    }
    if (!matches) {
      users.updateLoginState(
          user.registerFailedAttempt(now, lockout.maxAttempts(), lockout.duration()));
      return REJECTED;
    }
    if (user.failedAttempts() > 0 || user.lockedUntil() != null) {
      users.updateLoginState(user.registerSuccessfulLogin(now));
    }
    return new AuthenticationResult.Authenticated(
        new AuthenticatedUser(user.id(), user.username(), user.displayName(), user.roles()));
  }
}
