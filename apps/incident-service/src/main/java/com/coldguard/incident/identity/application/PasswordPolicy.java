package com.coldguard.incident.identity.application;

public record PasswordPolicy(int minLength) {

  /** bcrypt only uses the first 72 bytes; longer inputs are refused rather than truncated. */
  static final int MAX_LENGTH = 128;

  void validate(String password) {
    if (password == null || password.length() < minLength) {
      throw new IllegalArgumentException(
          "password must have at least %d characters".formatted(minLength));
    }
    if (password.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "password must have at most %d characters".formatted(MAX_LENGTH));
    }
  }
}
