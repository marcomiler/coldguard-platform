package com.coldguard.gateway.api.asset;

/** How critical a cold unit is; the REST vocabulary, independent of the gRPC contract. */
public enum Criticality {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
