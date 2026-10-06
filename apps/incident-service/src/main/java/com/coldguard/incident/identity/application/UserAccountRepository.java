package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

  Optional<UserAccount> findByUsername(String normalizedUsername);

  /** Locks the row until the surrounding transaction ends, serializing logins of one user. */
  Optional<UserAccount> findByUsernameForUpdate(String normalizedUsername);

  Optional<UserAccount> findById(UUID id);

  /** Locks the row until the surrounding transaction ends. */
  Optional<UserAccount> findByIdForUpdate(UUID id);

  /** Users ordered by username; {@code page} is zero-based. */
  List<UserAccount> findPage(int page, int size);

  long count();

  List<UserAccount> findEnabledByRole(Role role);

  /**
   * Counts the enabled holders of {@code role} other than {@code excludedUserId}, locking those
   * rows until the transaction ends so two concurrent changes cannot each see the other holder.
   */
  long countOtherEnabledWithRoleForUpdate(Role role, UUID excludedUserId);

  /**
   * @throws UserAlreadyExistsException if the username or email is taken
   */
  void insert(UserAccount user, String assignedBy);

  void updateLoginState(UserAccount user);

  /**
   * Persists the enabled flag and the role set; roles added now are recorded as {@code assignedBy}.
   */
  void updateAccess(UserAccount user, String assignedBy);
}
