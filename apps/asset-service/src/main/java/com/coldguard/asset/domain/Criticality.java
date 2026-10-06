package com.coldguard.asset.domain;

/** How critical a cold unit is to the business; drives incident priority downstream. */
public enum Criticality {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
