package com.coldguard.incident.infrastructure.messaging;

import com.coldguard.commons.messaging.ConsumerQueues;
import com.coldguard.commons.messaging.MessagingProperties;
import java.time.Duration;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queues this service consumes from, with their dead-letter queues and bindings. */
@Configuration(proxyBeanMethods = false)
public class IncidentMessagingTopology {

  /** Incident lifecycle events that no service consumes yet; retained so they stay routable. */
  public static final String LIFECYCLE_EVENTS_QUEUE = "incident-service.lifecycle-events";

  private static final Duration LIFECYCLE_EVENTS_RETENTION = Duration.ofDays(7);
  private static final int LIFECYCLE_EVENTS_MAX_LENGTH = 10_000;

  public static final String AUDIT_QUEUE = "incident-service.audit";
  public static final String THRESHOLD_BREACHED_QUEUE =
      "incident-service.telemetry-threshold-breached";

  @Bean
  Declarables auditQueue(MessagingProperties properties) {
    return ConsumerQueues.declare(
        properties,
        AUDIT_QUEUE,
        "asset.asset-registered",
        "asset.asset-updated",
        "asset.operational-profile-updated",
        "asset.sensor-status-changed",
        "asset.sensor-reassigned",
        "asset.sensor-calibration-recorded",
        "asset.sensor-calibration-expired",
        "asset.sensor-retired",
        "telemetry.sensor-connectivity-lost",
        "notification.notification-failed");
  }

  @Bean
  Declarables thresholdBreachedQueue(MessagingProperties properties) {
    return ConsumerQueues.declare(
        properties, THRESHOLD_BREACHED_QUEUE, "telemetry.threshold-breached");
  }

  @Bean
  Declarables lifecycleEventsQueue(MessagingProperties properties) {
    return ConsumerQueues.declareRetained(
        properties,
        LIFECYCLE_EVENTS_QUEUE,
        LIFECYCLE_EVENTS_RETENTION,
        LIFECYCLE_EVENTS_MAX_LENGTH,
        "incident.incident-created",
        "incident.incident-acknowledged",
        "incident.incident-escalated",
        "incident.incident-closed");
  }
}
