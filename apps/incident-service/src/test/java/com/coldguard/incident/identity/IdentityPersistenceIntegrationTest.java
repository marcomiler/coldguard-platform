package com.coldguard.incident.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.application.AuthenticationResult;
import com.coldguard.incident.identity.application.ProvisionUserCommand;
import com.coldguard.incident.identity.application.ProvisionUserService;
import com.coldguard.incident.identity.application.UserAccountRepository;
import com.coldguard.incident.identity.application.UserAlreadyExistsException;
import com.coldguard.incident.identity.application.VerifyCredentialsService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Identity against a real PostgreSQL: migration, bootstrap, login and lockout persistence. */
@Testcontainers
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "coldguard.identity.bootstrap.enabled=true",
      "coldguard.identity.bootstrap.demo-password=demo-password-1",
      "coldguard.identity.bootstrap.emails.supervisor=supervisor.demo@example.test",
      "coldguard.identity.password.bcrypt-strength=4",
      "coldguard.identity.lockout.max-attempts=3"
    })
class IdentityPersistenceIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "incident");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  @Autowired VerifyCredentialsService verify;
  @Autowired ProvisionUserService provisioning;
  @Autowired UserAccountRepository users;
  @Autowired JdbcClient jdbc;

  @Test
  void bootstrapCreatesOneDemoUserPerRoleWithHashedPasswords() {
    assertThat(jdbc.sql("SELECT count(*) FROM identity.user_account").query(Long.class).single())
        .isEqualTo(Role.values().length);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM identity.user_account WHERE password_hash LIKE '{bcrypt}%'")
                .query(Long.class)
                .single())
        .isEqualTo(Role.values().length);
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM identity.user_account WHERE password_hash LIKE '%demo-password-1%'")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(users.findByUsername("technician").orElseThrow().roles())
        .containsExactly(Role.MAINTENANCE_TECHNICIAN);
  }

  @Test
  void bootstrapUsesTheConfiguredAddressAndKeepsThePlaceholderForTheRest() {
    assertThat(users.findByUsername("supervisor").orElseThrow().email())
        .isEqualTo("supervisor.demo@example.test");
    assertThat(users.findByUsername("technician").orElseThrow().email())
        .isEqualTo("technician@coldguard.local");
  }

  @Test
  void demoUserLogsInWithTheirRole() {
    var result = verify.verify("Supervisor", "demo-password-1");

    assertThat(result).isInstanceOf(AuthenticationResult.Authenticated.class);
    assertThat(((AuthenticationResult.Authenticated) result).user().roles())
        .containsExactly(Role.OPERATIONS_SUPERVISOR);
  }

  @Test
  void failedAttemptsAreCommittedAndLockTheAccountEvenForTheCorrectPassword() {
    for (int i = 0; i < 3; i++) {
      assertThat(verify.verify("auditor", "nope"))
          .isInstanceOf(AuthenticationResult.Rejected.class);
    }

    var stored = users.findByUsername("auditor").orElseThrow();
    assertThat(stored.failedAttempts()).isEqualTo(3);
    assertThat(stored.lockedUntil()).isNotNull();
    assertThat(verify.verify("auditor", "demo-password-1"))
        .isInstanceOf(AuthenticationResult.Rejected.class);
  }

  @Test
  void duplicateUsernameOrEmailIsRejectedByTheDatabase() {
    ProvisionUserCommand sameUsername =
        new ProvisionUserCommand(
            "OPERATOR",
            "other@coldguard.local",
            "Dup",
            "long-enough-pw",
            Set.of(Role.OPERATOR),
            "t");
    ProvisionUserCommand sameEmail =
        new ProvisionUserCommand(
            "someone-else",
            "Operator@coldguard.local",
            "Dup",
            "long-enough-pw",
            Set.of(Role.OPERATOR),
            "t");

    assertThatThrownBy(() -> provisioning.provision(sameUsername))
        .isInstanceOf(UserAlreadyExistsException.class);
    assertThatThrownBy(() -> provisioning.provision(sameEmail))
        .isInstanceOf(UserAlreadyExistsException.class);
  }

  @Autowired com.coldguard.incident.identity.application.UserAdministrationService administration;

  private static final com.coldguard.commons.security.Actor ADMIN_ACTOR =
      new com.coldguard.commons.security.Actor("admin-test", Set.of(Role.PLATFORM_ADMIN));

  private com.coldguard.incident.identity.domain.UserAccount newUser(String username) {
    return administration.create(
        ADMIN_ACTOR,
        new ProvisionUserCommand(
            username,
            username + "@example.com",
            username,
            "long-enough-pw",
            Set.of(Role.AUDITOR),
            null));
  }

  @Test
  void roleChangesArePersistedWithTheActorThatMadeThem() {
    var user = newUser("roles-user");
    String id = user.id().toString();

    administration.assignRole(ADMIN_ACTOR, id, Role.OPERATOR, "covers shifts");
    assertThat(users.findById(user.id()).orElseThrow().roles())
        .containsExactlyInAnyOrder(Role.AUDITOR, Role.OPERATOR);
    assertThat(
            jdbc.sql(
                    "SELECT assigned_by FROM identity.user_role WHERE user_id = ? AND role = 'OPERATOR'")
                .param(user.id())
                .query(String.class)
                .single())
        .isEqualTo("admin-test");

    administration.revokeRole(ADMIN_ACTOR, id, Role.AUDITOR, "moved");
    assertThat(users.findById(user.id()).orElseThrow().roles()).containsExactly(Role.OPERATOR);
  }

  @Test
  void aDisabledUserCannotLogIn() {
    var user = newUser("disabled-user");
    assertThat(verify.verify("disabled-user", "long-enough-pw"))
        .isInstanceOf(AuthenticationResult.Authenticated.class);

    administration.setEnabled(ADMIN_ACTOR, user.id().toString(), false, "left");

    assertThat(verify.verify("disabled-user", "long-enough-pw"))
        .isInstanceOf(AuthenticationResult.Rejected.class);
    assertThat(users.findEnabledByRole(Role.AUDITOR))
        .extracting(com.coldguard.incident.identity.domain.UserAccount::username)
        .doesNotContain("disabled-user");
  }

  @Test
  void administratorsAreCountedExcludingTheTargetAndPagingIsOrdered() {
    var admin = newUser("second-admin");
    administration.assignRole(
        ADMIN_ACTOR, admin.id().toString(), Role.PLATFORM_ADMIN, "second administrator");

    assertThat(users.countOtherEnabledWithRoleForUpdate(Role.PLATFORM_ADMIN, admin.id()))
        .isGreaterThanOrEqualTo(1);
    assertThat(users.findPage(0, 3))
        .hasSize(3)
        .isSortedAccordingTo(
            java.util.Comparator.comparing(
                com.coldguard.incident.identity.domain.UserAccount::username));
    assertThat(users.count()).isGreaterThanOrEqualTo(Role.values().length + 1);
  }
}
