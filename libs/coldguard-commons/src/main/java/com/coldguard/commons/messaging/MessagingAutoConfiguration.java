package com.coldguard.commons.messaging;

import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.commons.messaging.inbox.InboxProperties;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboxMetrics;
import com.coldguard.commons.messaging.outbox.OutboxProperties;
import com.coldguard.commons.messaging.outbox.OutboxRelay;
import com.coldguard.commons.messaging.outbox.OutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Reliable messaging building blocks: transactional outbox, relay, idempotent-consumer guard and
 * topology. Active only in services that bring AMQP and JDBC.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
    })
@ConditionalOnClass({RabbitTemplate.class, JdbcClient.class})
@ConditionalOnBean({RabbitTemplate.class, JdbcClient.class, PlatformTransactionManager.class})
@EnableConfigurationProperties({
  MessagingProperties.class,
  OutboxProperties.class,
  InboxProperties.class
})
@EnableScheduling
public class MessagingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock messagingClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  EnvelopeCodec envelopeCodec(ObjectMapper mapper) {
    return new EnvelopeCodec(mapper);
  }

  /** Both exchanges are declared by every service so any of them can start first. */
  @Bean
  Declarables coldguardExchanges(MessagingProperties props) {
    return new Declarables(
        new TopicExchange(props.exchange(), true, false),
        new TopicExchange(props.deadLetterExchange(), true, false));
  }

  /**
   * Declares the exchanges and this service's queues as soon as it starts. Spring AMQP otherwise
   * declares them only when the first connection is opened, and a service that only publishes, or
   * has no listener yet, never opens one: a queue bound to another service's events would then not
   * exist and those events would be unroutable. Best effort: if the broker is not reachable now,
   * the declaration still happens on the first connection.
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "coldguard.messaging",
      name = "declare-on-startup",
      matchIfMissing = true)
  ApplicationRunner topologyDeclaration(AmqpAdmin admin) {
    return args -> {
      try {
        admin.initialize();
      } catch (AmqpException e) {
        LoggerFactory.getLogger(MessagingAutoConfiguration.class)
            .warn(
                "Broker not reachable at startup; the topology is declared on the first connection",
                e);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  InboxGuard inboxGuard(
      JdbcClient jdbc, PlatformTransactionManager txManager, InboxProperties props, Clock clock) {
    return new InboxGuard(jdbc, txManager, props, clock);
  }

  /** Permanent errors skip listener retries and go straight to the dead-letter queue. */
  @Bean
  RabbitListenerRetrySettingsCustomizer permanentErrorsAreNotRetried() {
    return settings -> {
      settings.getExceptionExcludes().add(AmqpRejectAndDontRequeueException.class);
      settings.getExceptionExcludes().add(MessageConversionException.class);
    };
  }

  @Bean
  @ConditionalOnMissingBean(DomainEventPublisher.class)
  OutboxWriter outboxWriter(
      JdbcClient jdbc,
      EnvelopeCodec codec,
      ObjectMapper mapper,
      Clock clock,
      @Value("${spring.application.name}") String producer) {
    return new OutboxWriter(jdbc, codec, mapper, producer, clock);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "coldguard.outbox", name = "enabled", matchIfMissing = true)
  OutboxRelay outboxRelay(
      JdbcClient jdbc,
      PlatformTransactionManager txManager,
      RabbitTemplate rabbit,
      MessagingProperties messaging,
      OutboxProperties props,
      ObjectMapper mapper,
      ObjectProvider<MeterRegistry> meterRegistry,
      Clock clock) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    OutboxMetrics metrics = registry != null ? new OutboxMetrics(registry, jdbc) : null;
    return new OutboxRelay(jdbc, txManager, rabbit, messaging, props, mapper, metrics, clock);
  }
}
