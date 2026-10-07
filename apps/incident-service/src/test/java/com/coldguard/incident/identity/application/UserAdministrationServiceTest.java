package com.coldguard.incident.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.auditlog.application.AuditEntry;
import com.coldguard.incident.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserAdministrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final Actor ADMIN = new Actor("admin-1", Set.of(Role.PLATFORM_ADMIN));
  private static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));

  private final InMemoryUserAccounts users = new InMemoryUserAccounts();
  private final List<AuditEntry> audited = new ArrayList<>();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final UserAdministrationService service =
      new UserAdministrationService(
          users,
          new ProvisionUserService(
              users, new BCryptPasswordEncoder(4), new PasswordPolicy(8), audited::add, clock),
          audited::add,
          clock);

  private UserAccount existing(String username, boolean enabled, Role... roles) {
    UserAccount user =
        UserAccount.register(
                username, username + "@example.com", username, "{bcrypt}x", Set.of(roles), NOW)
            .withEnabled(enabled, NOW);
    users.insert(user, "seed");
    return user;
  }

  @Test
  void everyOperationRequiresThePlatformAdminRole() {
    UserAccount user = existing("marta", true, Role.AUDITOR);
    String id = user.id().toString();

    for (Actor caller : new Actor[] {null, SUPERVISOR, Actor.system("job")}) {
      assertThatThrownBy(() -> service.get(caller, id))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(() -> service.list(caller, 0, 10))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(() -> service.assignRole(caller, id, Role.OPERATOR, "why"))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(() -> service.revokeRole(caller, id, Role.AUDITOR, "why"))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(() -> service.setEnabled(caller, id, false, "why"))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(() -> service.listContacts(caller, Role.AUDITOR))
          .isInstanceOf(IdentityAccessDeniedException.class);
      assertThatThrownBy(
              () ->
                  service.create(
                      caller,
                      new ProvisionUserCommand(
                          "new",
                          "new@example.com",
                          "New",
                          "long-enough-pw",
                          Set.of(Role.AUDITOR),
                          null)))
          .isInstanceOf(IdentityAccessDeniedException.class);
    }
    assertThat(users.byUsername).hasSize(1);
    assertThat(audited).isEmpty();
  }

  @Test
  void createAuditsTheCallerAsTheActor() {
    UserAccount created =
        service.create(
            ADMIN,
            new ProvisionUserCommand(
                "new",
                "new@example.com",
                "New",
                "long-enough-pw",
                Set.of(Role.AUDITOR),
                "ignored"));

    assertThat(created.roles()).containsExactly(Role.AUDITOR);
    assertThat(audited)
        .singleElement()
        .satisfies(e -> assertThat(e.actorId()).isEqualTo("admin-1"));
  }

  @Test
  void assignRoleAddsItAndAuditsActorReasonBeforeAndAfter() {
    UserAccount user = existing("marta", true, Role.AUDITOR);

    UserAccount updated =
        service.assignRole(ADMIN, user.id().toString(), Role.OPERATOR, "  covers night shift ");

    assertThat(updated.roles()).containsExactlyInAnyOrder(Role.AUDITOR, Role.OPERATOR);
    assertThat(users.findById(user.id()).orElseThrow().roles()).hasSize(2);
    assertThat(audited)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("ROLE_ASSIGNED");
              assertThat(e.entityId()).isEqualTo(user.id().toString());
              assertThat(e.actorId()).isEqualTo("admin-1");
              assertThat(e.reason()).isEqualTo("covers night shift");
              assertThat(e.before()).isEqualTo("enabled=true,roles=AUDITOR");
              assertThat(e.after()).isEqualTo("enabled=true,roles=AUDITOR|OPERATOR");
              assertThat(e.toString()).doesNotContain("bcrypt");
            });
  }

  @Test
  void repeatingAnAssignmentChangesAndAuditsNothing() {
    UserAccount user = existing("marta", true, Role.AUDITOR);

    service.assignRole(ADMIN, user.id().toString(), Role.AUDITOR, "again");

    assertThat(audited).isEmpty();
  }

  @Test
  void revokeRoleRemovesItAndAudits() {
    UserAccount user = existing("marta", true, Role.AUDITOR, Role.OPERATOR);

    UserAccount updated = service.revokeRole(ADMIN, user.id().toString(), Role.OPERATOR, "moved");

    assertThat(updated.roles()).containsExactly(Role.AUDITOR);
    assertThat(audited)
        .singleElement()
        .satisfies(e -> assertThat(e.action()).isEqualTo("ROLE_REVOKED"));
  }

  @Test
  void aReasonIsRequiredForEveryChange() {
    String id = existing("marta", true, Role.AUDITOR).id().toString();
    for (String reason : new String[] {null, "", "   "}) {
      assertThatThrownBy(() -> service.assignRole(ADMIN, id, Role.OPERATOR, reason))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reason");
      assertThatThrownBy(() -> service.revokeRole(ADMIN, id, Role.AUDITOR, reason))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> service.setEnabled(ADMIN, id, false, reason))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(audited).isEmpty();
  }

  @Test
  void theLastEnabledAdministratorCannotLoseTheRoleOrBeDisabled() {
    UserAccount only = existing("root", true, Role.PLATFORM_ADMIN);
    existing("disabled-admin", false, Role.PLATFORM_ADMIN);
    String id = only.id().toString();

    assertThatThrownBy(() -> service.revokeRole(ADMIN, id, Role.PLATFORM_ADMIN, "why"))
        .isInstanceOf(LastAdministratorException.class);
    assertThatThrownBy(() -> service.setEnabled(ADMIN, id, false, "why"))
        .isInstanceOf(LastAdministratorException.class);

    assertThat(users.findById(only.id()).orElseThrow().roles()).contains(Role.PLATFORM_ADMIN);
    assertThat(users.findById(only.id()).orElseThrow().enabled()).isTrue();
    assertThat(audited).isEmpty();
  }

  @Test
  void anAdministratorCanBeDemotedOrDisabledWhileAnotherEnabledOneRemains() {
    UserAccount first = existing("first", true, Role.PLATFORM_ADMIN);
    UserAccount second = existing("second", true, Role.PLATFORM_ADMIN);

    service.revokeRole(ADMIN, first.id().toString(), Role.PLATFORM_ADMIN, "left the team");

    assertThat(users.findById(first.id()).orElseThrow().roles()).isEmpty();
    assertThatThrownBy(
            () -> service.setEnabled(ADMIN, second.id().toString(), false, "would orphan"))
        .isInstanceOf(LastAdministratorException.class);
  }

  @Test
  void setEnabledDisablesAndReenablesWithAudit() {
    UserAccount user = existing("marta", true, Role.AUDITOR);
    String id = user.id().toString();

    assertThat(service.setEnabled(ADMIN, id, false, "left").enabled()).isFalse();
    assertThat(service.setEnabled(ADMIN, id, true, "back").enabled()).isTrue();
    service.setEnabled(ADMIN, id, true, "no-op");

    assertThat(audited)
        .extracting(AuditEntry::action)
        .containsExactly("USER_DISABLED", "USER_ENABLED");
  }

  @Test
  void unknownOrMalformedUserIdsAreNotFound() {
    assertThatThrownBy(() -> service.get(ADMIN, "not-a-uuid"))
        .isInstanceOf(UserNotFoundException.class);
    assertThatThrownBy(
            () ->
                service.assignRole(
                    ADMIN, java.util.UUID.randomUUID().toString(), Role.OPERATOR, "x"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void listIsPagedSortedAndBounded() {
    existing("carla", true, Role.AUDITOR);
    existing("ana", true, Role.AUDITOR);
    existing("bruno", true, Role.AUDITOR);

    var page = service.list(ADMIN, 1, 2);

    assertThat(page.users()).extracting(UserAccount::username).containsExactly("carla");
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThatThrownBy(() -> service.list(ADMIN, -1, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(ADMIN, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(ADMIN, 0, UserAdministrationService.MAX_PAGE_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void contactsAreEnabledHoldersOfTheRoleOnly() {
    existing("ana", true, Role.AUDITOR);
    existing("off", false, Role.AUDITOR);
    existing("other", true, Role.OPERATOR);

    assertThat(service.listContacts(ADMIN, Role.AUDITOR))
        .extracting(UserAccount::username)
        .containsExactly("ana");
  }
}
