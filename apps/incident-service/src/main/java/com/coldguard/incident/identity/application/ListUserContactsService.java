package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-process API other modules of this service use to find who holds a role. Not exposed over gRPC,
 * so it needs no actor.
 */
@Service
public class ListUserContactsService {

  private final UserAccountRepository users;

  ListUserContactsService(UserAccountRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<UserContact> enabledWithRole(Role role) {
    return users.findEnabledByRole(role).stream()
        .map(user -> new UserContact(user.id().toString(), user.email()))
        .toList();
  }
}
