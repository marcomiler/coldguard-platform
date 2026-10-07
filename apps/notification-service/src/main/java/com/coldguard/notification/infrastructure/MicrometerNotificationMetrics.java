package com.coldguard.notification.infrastructure;

import com.coldguard.notification.application.NotificationMetrics;
import com.coldguard.notification.domain.FailureCategory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** {@code coldguard.notification.sent} and {@code coldguard.notification.failed{category}}. */
@Component
class MicrometerNotificationMetrics implements NotificationMetrics {

  private final MeterRegistry registry;

  MicrometerNotificationMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void sent() {
    afterCommit(() -> registry.counter("coldguard.notification.sent").increment());
  }

  @Override
  public void failed(FailureCategory category) {
    afterCommit(
        () ->
            registry
                .counter("coldguard.notification.failed", "category", category.name())
                .increment());
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
