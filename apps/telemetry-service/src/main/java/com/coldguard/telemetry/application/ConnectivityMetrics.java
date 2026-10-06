package com.coldguard.telemetry.application;

/** Counts sensors found to have stopped reporting. */
public interface ConnectivityMetrics {

  void connectivityLost();
}
