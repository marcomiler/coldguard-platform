package com.coldguard.incident.identity.application;

public class UserAlreadyExistsException extends RuntimeException {

  public UserAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }
}
