package com.coldguard.asset.application;

import java.time.Duration;
import java.time.Instant;

/**
 * How long a calibration stays valid. The caller never supplies the expiry: it is the moment of the
 * calibration plus the profile's validity, or the configured default when the profile has none.
 */
public record CalibrationPolicy(Duration defaultValidity) {

  public Instant validUntil(Instant performedAt, Duration profileValidity, Instant now) {
    if (performedAt.isAfter(now)) {
      throw new IllegalArgumentException("performedAt cannot be in the future");
    }
    Duration validity = profileValidity != null ? profileValidity : defaultValidity;
    if (validity == null) {
      throw new CalibrationValidityNotConfiguredException();
    }
    return performedAt.plus(validity);
  }
}
