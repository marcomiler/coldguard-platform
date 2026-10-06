package com.coldguard.asset.domain;

/** A domain rule forbids the operation; {@link #code()} is the stable code of the contract. */
public abstract class BusinessRuleViolationException extends RuntimeException {

  private final String code;

  protected BusinessRuleViolationException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Stable business code published in the {@code x-error-code} gRPC trailer. */
  public String code() {
    return code;
  }
}
