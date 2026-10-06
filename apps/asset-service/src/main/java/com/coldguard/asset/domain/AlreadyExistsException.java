package com.coldguard.asset.domain;

/** A unique value (organization name, site name, sensor serial number) is already taken. */
public class AlreadyExistsException extends RuntimeException {

  private final String code;

  public AlreadyExistsException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Stable business code published in the {@code x-error-code} gRPC trailer. */
  public String code() {
    return code;
  }
}
