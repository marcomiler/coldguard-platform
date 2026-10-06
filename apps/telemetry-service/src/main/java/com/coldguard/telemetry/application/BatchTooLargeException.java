package com.coldguard.telemetry.application;

public class BatchTooLargeException extends RuntimeException {

  public BatchTooLargeException(int size, int max) {
    super("A batch has at most " + max + " readings (received " + size + ")");
  }
}
