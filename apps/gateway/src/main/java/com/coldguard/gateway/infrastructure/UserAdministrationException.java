package com.coldguard.gateway.infrastructure;

/** A user-administration call that Identity refused; {@link #kind()} says why. */
public class UserAdministrationException extends RuntimeException {

  public enum Kind {
    FORBIDDEN,
    NOT_FOUND,
    ALREADY_EXISTS,
    CONFLICT,
    INVALID
  }

  private final Kind kind;

  public UserAdministrationException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
