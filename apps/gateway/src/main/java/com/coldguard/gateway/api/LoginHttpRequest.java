package com.coldguard.gateway.api;

/** {@code password} is sensitive: the record never prints it. */
public record LoginHttpRequest(String username, String password) {

  @Override
  public String toString() {
    return "LoginHttpRequest[username=" + username + "]";
  }
}
