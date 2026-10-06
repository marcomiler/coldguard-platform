package com.coldguard.asset.application;

import com.coldguard.asset.domain.CalibrationRecord;

/** Append-only: there is deliberately no way to change or delete a record. */
public interface CalibrationRepository {

  void insert(CalibrationRecord record);
}
