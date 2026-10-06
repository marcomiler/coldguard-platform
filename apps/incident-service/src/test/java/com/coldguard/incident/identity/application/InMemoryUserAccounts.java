package com.coldguard.incident.identity.application;

import com.coldguard.incident.identity.domain.UserAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
}
