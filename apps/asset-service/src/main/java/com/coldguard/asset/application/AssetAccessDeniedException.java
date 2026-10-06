package com.coldguard.asset.application;

/** The caller is missing, or does not hold, a role the operation requires. */
public class AssetAccessDeniedException extends RuntimeException {

  public AssetAccessDeniedException(String message) {
    super(message);
  }
}
