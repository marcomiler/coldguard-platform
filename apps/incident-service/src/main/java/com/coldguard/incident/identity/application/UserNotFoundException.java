package com.coldguard.incident.identity.application;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String userId) {
    super("User not found: " + userId);
  }
}
