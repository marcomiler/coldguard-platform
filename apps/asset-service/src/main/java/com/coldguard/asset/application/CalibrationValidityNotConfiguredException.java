package com.coldguard.asset.application;

/** No calibration validity is defined by the profile or by the service default. */
public class CalibrationValidityNotConfiguredException extends RuntimeException {

  public CalibrationValidityNotConfiguredException() {
    super(
        "The sensor profile defines no calibration validity and no default is configured; "
            + "refusing to invent one");
  }
}
