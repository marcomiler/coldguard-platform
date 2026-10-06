package com.coldguard.asset.domain;

/** Returning to ACTIVE from maintenance needs a calibration recorded after entering it. */
public class CalibrationEvidenceRequiredException extends BusinessRuleViolationException {

  public CalibrationEvidenceRequiredException() {
    super(
        "CALIBRATION_EVIDENCE_REQUIRED",
        "A calibration or verification must be recorded after the sensor entered maintenance");
  }
}
