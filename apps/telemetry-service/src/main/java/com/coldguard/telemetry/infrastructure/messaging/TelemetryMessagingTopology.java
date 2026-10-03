package com.coldguard.telemetry.infrastructure.messaging;

import com.coldguard.commons.messaging.ConsumerQueues;
import com.coldguard.commons.messaging.MessagingProperties;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queues this service consumes from, with their dead-letter queues and bindings. */
@Configuration(proxyBeanMethods = false)
public class TelemetryMessagingTopology {

  public static final String ASSET_CHANGES_QUEUE = "telemetry-service.asset-changes";

  @Bean
  Declarables assetChangesQueue(MessagingProperties properties) {
    return ConsumerQueues.declare(
        properties,
        ASSET_CHANGES_QUEUE,
        "asset.asset-updated",
        "asset.operational-profile-updated",
        "asset.sensor-status-changed",
        "asset.sensor-reassigned",
        "asset.sensor-retired");
  }
}
