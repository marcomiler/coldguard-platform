package com.coldguard.incident.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.auditlog.application.AuditEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ProvisionUserServiceTest {

  private final InMemoryUserAccounts users = new InMemoryUserAccounts();
  private final List<AuditEntry> audited = new ArrayList<>();
  private final ProvisionUserService service =
      new ProvisionUserService(
          users,
          new BCryptPasswordEncoder(4),
          new PasswordPolicy(8),
          audited::add,
          Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));

  private ProvisionUserCommand command(String password) {
    return new ProvisionUserCommand(
        "marta", "marta@example.com", "Marta", password, Set.of(Role.AUDITOR), "admin-1");
  }

  @Test
  void storesAHashAndAuditsWithoutSecrets() {
    var user = service.provision(command("long-enough-pw"));

    assertThat(user.passwordHash()).isNotEqualTo("long-enough-pw").startsWith("$2");
    assertThat(audited).hasSize(1);
    AuditEntry entry = audited.get(0);
    assertThat(entry.action()).isEqualTo("USER_CREATED");
    assertThat(entry.actorId()).isEqualTo("admin-1");
    assertThat(entry.after()).isEqualTo("roles=AUDITOR");
    assertThat(entry.toString())
        .doesNotContain("long-enough-pw")
        .doesNotContain(user.passwordHash());
  }

  @Test
  void rejectsWeakPasswordsWithoutWritingAnything() {
    assertThatThrownBy(() -> service.provision(command("short")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(users.byUsername).isEmpty();
    assertThat(audited).isEmpty();
  }

  @Test
  void duplicateUsernameIsRejected() {
    service.provision(command("long-enough-pw"));

    assertThatThrownBy(() -> service.provision(command("long-enough-pw")))
        .isInstanceOf(UserAlreadyExistsException.class);
    assertThat(audited).hasSize(1);
  }

  @Test
  void commandToStringHidesThePassword() {
    assertThat(command("long-enough-pw").toString()).doesNotContain("long-enough-pw");
  }
}
