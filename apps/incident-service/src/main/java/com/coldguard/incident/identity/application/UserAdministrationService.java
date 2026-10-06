package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. Every operation requires the {@code PLATFORM_ADMIN} role, checked here and
 * not only at the edge; role and enablement changes need a reason and are audited in the same
 * transaction. The last enabled administrator can neither lose that role nor be disabled.
 */
@Service
public class UserAdministrationService {

  public static final int MAX_PAGE_SIZE = 100;

  private final UserAccountRepository users;
  private final ProvisionUserService provisioning;
  private final AuditRecorder audit;
  private final Clock clock;

  public UserAdministrationService(
      UserAccountRepository users,
      ProvisionUserService provisioning,
      AuditRecorder audit,
      Clock clock) {
    this.users = users;
    this.provisioning = provisioning;
    this.audit = audit;
    this.clock = clock;
  }

  public record UserPage(List<UserAccount> users, int page, int size, long totalElements) {

    public int totalPages() {
      return (int) ((totalElements + size - 1) / size);
    }
  }

  @Transactional
  public UserAccount create(Actor actor, ProvisionUserCommand command) {
    requireAdmin(actor);
    return provisioning.provision(
        new ProvisionUserCommand(
            command.username(),
            command.email(),
            command.displayName(),
            command.password(),
            command.roles(),
            actor.id()));
  }

  @Transactional(readOnly = true)
  public UserAccount get(Actor actor, String userId) {
    requireAdmin(actor);
    return users.findById(parse(userId)).orElseThrow(() -> new UserNotFoundException(userId));
  }

  @Transactional(readOnly = true)
  public UserPage list(Actor actor, int page, int size) {
    requireAdmin(actor);
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return new UserPage(users.findPage(page, size), page, size, users.count());
  }

  @Transactional
  public UserAccount assignRole(Actor actor, String userId, Role role, String reason) {
    requireAdmin(actor);
    requireReason(reason);
    UserAccount before = lock(userId);
    if (before.roles().contains(role)) {
      return before;
    }
    UserAccount after = before.withRole(role, clock.instant());
    users.updateAccess(after, actor.id());
    record("ROLE_ASSIGNED", actor, reason, before, after);
    return after;
  }

  @Transactional
  public UserAccount revokeRole(Actor actor, String userId, Role role, String reason) {
    requireAdmin(actor);
    requireReason(reason);
    UserAccount before = lock(userId);
    if (!before.roles().contains(role)) {
      return before;
    }
    if (role == Role.PLATFORM_ADMIN && before.enabled()) {
      requireAnotherAdministrator(before);
    }
    UserAccount after = before.withoutRole(role, clock.instant());
    users.updateAccess(after, actor.id());
    record("ROLE_REVOKED", actor, reason, before, after);
    return after;
  }

  @Transactional
  public UserAccount setEnabled(Actor actor, String userId, boolean enabled, String reason) {
    requireAdmin(actor);
    requireReason(reason);
    UserAccount before = lock(userId);
    if (before.enabled() == enabled) {
      return before;
    }
    if (!enabled && before.roles().contains(Role.PLATFORM_ADMIN)) {
      requireAnotherAdministrator(before);
    }
    UserAccount after = before.withEnabled(enabled, clock.instant());
    users.updateAccess(after, actor.id());
    record(enabled ? "USER_ENABLED" : "USER_DISABLED", actor, reason, before, after);
    return after;
  }

  @Transactional(readOnly = true)
  public List<UserAccount> listContacts(Actor actor, Role role) {
    requireAdmin(actor);
    return users.findEnabledByRole(role);
  }

  private UserAccount lock(String userId) {
    return users
        .findByIdForUpdate(parse(userId))
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  private void requireAnotherAdministrator(UserAccount target) {
    if (users.countOtherEnabledWithRoleForUpdate(Role.PLATFORM_ADMIN, target.id()) == 0) {
      throw new LastAdministratorException();
    }
  }

  private void record(
      String action, Actor actor, String reason, UserAccount before, UserAccount after) {
    audit.record(
        new AuditEntry(
            action,
            "UserAccount",
            after.id().toString(),
            actor.id(),
            reason.strip(),
            describe(before),
            describe(after),
            after.updatedAt()));
  }

  /** Never includes the password hash. */
  private static String describe(UserAccount user) {
    return "enabled=%s,roles=%s".formatted(user.enabled(), roles(user.roles()));
  }

  private static String roles(Set<Role> roles) {
    return roles.stream().map(Role::name).sorted().collect(Collectors.joining("|"));
  }

  private static void requireAdmin(Actor actor) {
    if (actor == null || !actor.hasRole(Role.PLATFORM_ADMIN)) {
      throw new IdentityAccessDeniedException("PLATFORM_ADMIN role required");
    }
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
  }

  private static UUID parse(String userId) {
    try {
      return UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      throw new UserNotFoundException(userId);
    }
  }
}
