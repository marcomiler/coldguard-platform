package com.coldguard.asset.domain;

/** The requested status change, or any change to a retired sensor, is not allowed. */
public class SensorTransitionNotAllowedException extends BusinessRuleViolationException {

  public SensorTransitionNotAllowedException(String message) {
    super("SENSOR_TRANSITION_NOT_ALLOWED", message);
  }
}
