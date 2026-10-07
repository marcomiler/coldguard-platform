package com.coldguard.incident.domain;

/** The cause and resolution comment a technician must give to close an incident. */
public record CloseEvidence(String cause, String resolutionComment) {

  public static final int MAX_CAUSE_LENGTH = 500;
  public static final int MAX_COMMENT_LENGTH = 2000;

  public CloseEvidence {
    cause = requireBounded(cause, "cause", MAX_CAUSE_LENGTH);
    resolutionComment = requireBounded(resolutionComment, "resolutionComment", MAX_COMMENT_LENGTH);
  }

  static String requireBounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
    }
    return trimmed;
  }
}
