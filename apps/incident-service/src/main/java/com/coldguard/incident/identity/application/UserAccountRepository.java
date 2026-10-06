package com.coldguard.incident.identity.application;

import com.coldguard.incident.identity.domain.UserAccount;
import java.util.Optional;

public interface UserAccountRepository {

  Optional<UserAccount> findByUsername(String normalizedUsername);

  /** Locks the row until the surrounding transaction ends, serializing logins of one user. */
  Optional<UserAccount> findByUsernameForUpdate(String normalizedUsername);

  /**
   * @throws UserAlreadyExistsException if the username or email is taken
   */
  void insert(UserAccount user, String assignedBy);

  void updateLoginState(UserAccount user);
}
