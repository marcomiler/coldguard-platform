package com.coldguard.telemetry.application;

/** Asset could not say what a sensor's context is (down, or too slow); nothing was stored. */
public class AssetUnavailableException extends RuntimeException {

  public AssetUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
