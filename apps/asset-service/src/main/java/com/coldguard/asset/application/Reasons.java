package com.coldguard.asset.application;

/** The reason every administrative change to a sensor must carry (RN-017). */
final class Reasons {

  static final int MAX_LENGTH = 500;

  private Reasons() {}

  static String required(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    String stripped = reason.strip();
    if (stripped.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("reason must have at most " + MAX_LENGTH + " characters");
    }
    return stripped;
  }
}
