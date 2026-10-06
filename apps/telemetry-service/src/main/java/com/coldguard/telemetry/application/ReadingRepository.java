package com.coldguard.telemetry.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReadingRepository {

  /** The ids, among {@code ids}, that are already stored. */
  Set<UUID> findExisting(Collection<UUID> ids);

  /** Inserts the readings; one that already exists is skipped. */
  void insertAll(List<StoredReading> readings);

  /**
   * Readings of one sensor with {@code from <= recorded_at < to}, newest first by {@code
   * (recorded_at, id)}, strictly after {@code after} (null starts at the newest). Returns at most
   * {@code limit}.
   */
  List<StoredReading> findPage(
      UUID sensorId, Instant from, Instant to, ReadingCursor after, int limit);
}
