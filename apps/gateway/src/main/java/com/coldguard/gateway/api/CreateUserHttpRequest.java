package com.coldguard.gateway.api;

import java.util.List;

/** {@code initialPassword} is sensitive: the record never prints it. */
public record CreateUserHttpRequest(
    String username, String email, String displayName, String initialPassword, List<String> roles) {

  @Override
  public String toString() {
    return "CreateUserHttpRequest[username=" + username + ", roles=" + roles + "]";
  }
}
