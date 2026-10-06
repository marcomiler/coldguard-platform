package com.coldguard.asset.application;

import com.coldguard.asset.domain.OperationalProfile;
import java.util.Optional;
import java.util.UUID;

public interface OperationalProfileRepository {

  Optional<OperationalProfile> findBySensorId(UUID sensorId);

  /**
   * Creates the profile when {@code expectedVersion} is 0, otherwise updates it if the stored
   * version equals {@code expectedVersion}. Returns it with its new version (1 when created).
   *
   * @throws com.coldguard.asset.domain.StaleVersionException on a version mismatch
   */
  OperationalProfile save(OperationalProfile profile, long expectedVersion);
}
