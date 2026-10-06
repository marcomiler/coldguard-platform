package com.coldguard.asset.domain;

/** Input normalization shared by the aggregates; failures are caller errors, not server errors. */
final class Text {

  private Text() {}

  static String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return limited(value.strip(), field, maxLength);
  }

  /** Blank becomes null. */
  static String optional(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return limited(value.strip(), field, maxLength);
  }

  private static String limited(String value, String field, int maxLength) {
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must have at most " + maxLength + " characters");
    }
    return value;
  }
}
