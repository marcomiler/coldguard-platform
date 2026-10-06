package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.time.Clock;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a user with its initial roles. It does not check who is asking: the caller (local
 * bootstrap today, an admin-only endpoint later) is responsible for authorization.
 */
@Service
public class ProvisionUserService {

  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final PasswordPolicy passwordPolicy;
  private final AuditRecorder audit;
  private final Clock clock;

  public ProvisionUserService(
      UserAccountRepository users,
      PasswordEncoder encoder,
      PasswordPolicy passwordPolicy,
      AuditRecorder audit,
      Clock clock) {
    this.users = users;
    this.encoder = encoder;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public UserAccount provision(ProvisionUserCommand command) {
    passwordPolicy.validate(command.password());
    UserAccount user =
        UserAccount.register(
            command.username(),
            command.email(),
            command.displayName(),
            encoder.encode(command.password()),
            command.roles(),
            clock.instant());
    users.insert(user, command.actorId());
    audit.record(
        new AuditEntry(
            "USER_CREATED",
            "UserAccount",
            user.id().toString(),
            command.actorId(),
            null,
            null,
            "roles=" + describe(user),
            user.createdAt()));
    return user;
  }

  private static String describe(UserAccount user) {
    return user.roles().stream()
        .sorted(Comparator.comparing(Role::name))
        .map(Role::name)
        .collect(Collectors.joining(","));
  }
}
