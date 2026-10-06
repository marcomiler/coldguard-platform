package com.coldguard.telemetry.application;

public class RangeTooWideException extends RuntimeException {

  public RangeTooWideException(String message) {
    super(message);
  }
}
