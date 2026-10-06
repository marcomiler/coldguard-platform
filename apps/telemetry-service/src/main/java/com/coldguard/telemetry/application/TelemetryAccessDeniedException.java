package com.coldguard.telemetry.application;

public class TelemetryAccessDeniedException extends RuntimeException {

  public TelemetryAccessDeniedException(String message) {
    super(message);
  }
}
