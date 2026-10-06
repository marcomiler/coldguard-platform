package com.coldguard.incident.identity.application;

/** The caller is missing, or does not hold the role required for a user-administration action. */
public class IdentityAccessDeniedException extends RuntimeException {

  public IdentityAccessDeniedException(String message) {
    super(message);
  }
}
