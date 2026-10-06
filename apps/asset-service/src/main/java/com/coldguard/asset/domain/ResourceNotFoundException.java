package com.coldguard.asset.domain;

/** A referenced organization, site, asset, sensor or profile does not exist. */
public class ResourceNotFoundException extends RuntimeException {

  private final String code;

  public ResourceNotFoundException(String kind, Object id) {
    super(kind + " not found: " + id);
    this.code =
        switch (kind) {
          case "OperationalProfile" -> "PROFILE_NOT_FOUND";
          default -> kind.toUpperCase(java.util.Locale.ROOT) + "_NOT_FOUND";
        };
  }

  /** Stable business code published in the {@code x-error-code} gRPC trailer. */
  public String code() {
    return code;
  }
}
