package com.coldguard.notification.domain;

/** Why a delivery or a whole notification failed for good (matches NotificationFailed v1). */
public enum FailureCategory {
  PERMANENT,
  RETRIES_EXHAUSTED
}
