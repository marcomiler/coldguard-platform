package com.coldguard.asset.application;

/** Append-only history of a sensor: assignments and administrative changes. */
public interface SensorHistoryRepository {

  void addAssignment(AssignmentEntry entry);

  void addEntry(SensorHistoryEntry entry);
}
