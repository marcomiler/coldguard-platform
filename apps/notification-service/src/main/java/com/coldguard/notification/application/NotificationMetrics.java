package com.coldguard.notification.application;

import com.coldguard.notification.domain.FailureCategory;

/** Delivery outcomes as metrics; implementations count only once the transaction has committed. */
public interface NotificationMetrics {

  void sent();

  void failed(FailureCategory category);
}
