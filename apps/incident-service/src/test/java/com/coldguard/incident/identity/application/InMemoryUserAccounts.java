package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryUserAccounts implements UserAccountRepository {

  final Map<String, UserAccount> byUsername = new HashMap<>();

  @Override
  public Optional<UserAccount> findByUsername(String normalizedUsername) {
    return Optional.ofNullable(byUsername.get(normalizedUsername));
  }

  @Override
  public Optional<UserAccount> findByUsernameForUpdate(String normalizedUsername) {
    return findByUsername(normalizedUsername);
  }

  @Override
  public void insert(UserAccount user, String assignedBy) {
    if (byUsername.containsKey(user.username())) {
      throw new UserAlreadyExistsException("taken", null);
    }
    byUsername.put(user.username(), user);
  }

  @Override
  public void updateLoginState(UserAccount user) {
    byUsername.put(user.username(), user);
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return byUsername.values().stream().filter(u -> u.id().equals(id)).findFirst();
  }

  @Override
  public Optional<UserAccount> findByIdForUpdate(UUID id) {
    return findById(id);
  }

  @Override
  public List<UserAccount> findPage(int page, int size) {
    return byUsername.values().stream()
        .sorted(Comparator.comparing(UserAccount::username))
        .skip((long) page * size)
        .limit(size)
        .toList();
  }

  @Override
  public long count() {
    return byUsername.size();
  }

  @Override
  public List<UserAccount> findEnabledByRole(Role role) {
    return byUsername.values().stream()
        .filter(u -> u.enabled() && u.roles().contains(role))
        .sorted(Comparator.comparing(UserAccount::username))
        .toList();
  }

  @Override
  public long countOtherEnabledWithRoleForUpdate(Role role, UUID excludedUserId) {
    return byUsername.values().stream()
        .filter(u -> u.enabled() && u.roles().contains(role) && !u.id().equals(excludedUserId))
        .count();
  }

  @Override
  public void updateAccess(UserAccount user, String assignedBy) {
    byUsername.put(user.username(), user);
  }
}
