package com.coldguard.gateway.infrastructure;

/** Identity rejected the credentials; carries no reason on purpose. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid credentials");
  }
}
