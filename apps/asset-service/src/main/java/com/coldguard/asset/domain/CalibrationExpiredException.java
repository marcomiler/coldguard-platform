package com.coldguard.asset.domain;

/** The sensor has no calibration or verification that is still valid. */
public class CalibrationExpiredException extends BusinessRuleViolationException {

  public CalibrationExpiredException(String message) {
    super("CALIBRATION_EXPIRED", message);
  }
}
