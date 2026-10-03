package com.coldguard.notification.infrastructure.messaging;

import com.coldguard.commons.messaging.ConsumerQueues;
import com.coldguard.commons.messaging.MessagingProperties;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queues this service consumes from, with their dead-letter queues and bindings. */
@Configuration(proxyBeanMethods = false)
public class NotificationMessagingTopology {

  public static final String NOTIFICATION_REQUESTED_QUEUE =
      "notification-service.notification-requested";

  @Bean
  Declarables notificationRequestedQueue(MessagingProperties properties) {
    return ConsumerQueues.declare(
        properties, NOTIFICATION_REQUESTED_QUEUE, "incident.notification-requested");
  }
}
