package com.coldguard.asset.application;

import java.util.List;
import java.util.UUID;

/** Append-only history of a sensor: assignments and administrative changes. */
public interface SensorHistoryRepository {

  void addAssignment(AssignmentEntry entry);

  void addEntry(SensorHistoryEntry entry);

  /**
   * Entries of one sensor, newest first by {@code (occurredAt, id)}, strictly after {@code cursor}
   * (null starts at the newest). Returns at most {@code limit} entries.
   */
  List<SensorHistoryEntry> findPage(UUID sensorId, HistoryCursor cursor, int limit);
}
