package com.coldguard.incident.identity.infrastructure;

import com.coldguard.incident.identity.application.ProvisionUserCommand;
import com.coldguard.incident.identity.application.ProvisionUserService;
import com.coldguard.incident.identity.application.UserAccountRepository;
import com.coldguard.incident.identity.domain.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-only: creates one demo user per role if missing, all sharing the password from {@code
 * DEMO_USERS_PASSWORD}. Refuses to start when enabled without that password.
 */
@Component
@ConditionalOnProperty(
    prefix = "coldguard.identity.bootstrap",
    name = "enabled",
    havingValue = "true")
class IdentityBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(IdentityBootstrap.class);
  private static final String ACTOR = "identity-bootstrap";

  private final IdentityProperties props;
  private final UserAccountRepository users;
  private final ProvisionUserService provisioning;

  IdentityBootstrap(
      IdentityProperties props, UserAccountRepository users, ProvisionUserService provisioning) {
    this.props = props;
    this.users = users;
    this.provisioning = provisioning;
  }

  @Override
  public void run(ApplicationArguments args) {
    String password = props.bootstrap().demoPassword();
    if (password == null || password.isBlank()) {
      throw new IllegalStateException(
          "coldguard.identity.bootstrap.enabled requires DEMO_USERS_PASSWORD to be set");
    }
    for (Role role : Role.values()) {
      String username = demoUsername(role);
      if (users.findByUsername(UserAccount.normalizeUsername(username)).isPresent()) {
        continue;
      }
      provisioning.provision(
          new ProvisionUserCommand(
              username,
              username + "@coldguard.local",
              "Demo " + role.name().toLowerCase().replace('_', ' '),
              password,
              Set.of(role),
              ACTOR));
      log.info("Created demo user {}", username);
    }
  }

  static String demoUsername(Role role) {
    return switch (role) {
      case OPERATIONS_SUPERVISOR -> "supervisor";
      case OPERATOR -> "operator";
      case MAINTENANCE_TECHNICIAN -> "technician";
      case AUDITOR -> "auditor";
      case PLATFORM_ADMIN -> "admin";
    };
  }
}
