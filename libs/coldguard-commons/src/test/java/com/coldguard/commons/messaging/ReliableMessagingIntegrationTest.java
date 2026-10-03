package com.coldguard.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.messaging.outbox.OutboxProperties;
import com.coldguard.commons.messaging.outbox.OutboxRelay;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
    classes = ReliableMessagingIntegrationTest.TestApp.class,
    properties = {
      "spring.application.name=test-service",
      "spring.main.web-application-type=none",
      "coldguard.outbox.poll-interval=1h",
      "coldguard.outbox.confirm-timeout=5s",
      "coldguard.outbox.batch-size=5",
      "coldguard.outbox.max-attempts=3",
      "coldguard.messaging.queue-type=quorum",
      "spring.rabbitmq.publisher-confirm-type=correlated",
      "spring.rabbitmq.publisher-returns=true",
      "spring.rabbitmq.template.mandatory=true",
      "spring.rabbitmq.listener.simple.retry.enabled=true",
      "spring.rabbitmq.listener.simple.retry.max-retries=2",
      "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
      "spring.rabbitmq.listener.simple.default-requeue-rejected=false"
    })
class ReliableMessagingIntegrationTest {

  static final String QUEUE = "test-service.events";
  static final String ROUTING_KEY = "test.thing-happened";
  static final String RETAINED_QUEUE = "test-service.retained";
  static final String RETAINED_KEY = "test.retained";

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void connections(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  @Configuration
  @EnableAutoConfiguration
  static class TestApp {

    static final AtomicInteger EFFECTS = new AtomicInteger();

    @Bean
    Declarables testQueues(MessagingProperties props) {
      return ConsumerQueues.declare(props, QUEUE, ROUTING_KEY);
    }

    @Bean
    Declarables retainedQueue(MessagingProperties props) {
      return ConsumerQueues.declareRetained(
          props, RETAINED_QUEUE, Duration.ofDays(1), 100, RETAINED_KEY);
    }

    @Bean
    Consumer consumer(InboxGuard inbox, EnvelopeCodec codec) {
      return new Consumer(inbox, codec);
    }
  }

  static class Consumer {
    private final InboxGuard inbox;
    private final EnvelopeCodec codec;

    Consumer(InboxGuard inbox, EnvelopeCodec codec) {
      this.inbox = inbox;
      this.codec = codec;
    }

    @RabbitListener(queues = QUEUE)
    void on(Message message) {
      EventEnvelope envelope = codec.read(message.getBody());
      inbox.runOnce(envelope.eventId(), "test-consumer", TestApp.EFFECTS::incrementAndGet);
    }
  }

  @Autowired DomainEventPublisher publisher;
  @Autowired OutboxRelay relay;
  @Autowired InboxGuard inbox;
  @Autowired JdbcClient jdbc;
  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired AmqpAdmin admin;
  @Autowired PlatformTransactionManager txManager;
  @Autowired MessagingProperties messaging;
  @Autowired ObjectMapper objectMapper;

  private TransactionTemplate tx;

  @BeforeEach
  void clean() {
    tx = new TransactionTemplate(txManager);
    jdbc.sql("DELETE FROM outbox_event").update();
    jdbc.sql("DELETE FROM processed_message").update();
    TestApp.EFFECTS.set(0);
    CorrelationContext.clear();
    admin.purgeQueue(QUEUE, false);
    admin.purgeQueue(RETAINED_QUEUE, false);
    admin.purgeQueue(ConsumerQueues.deadLetterQueueName(QUEUE), false);
  }

  private OutboundEvent event(String routingKey) {
    return event(routingKey, "thing-1");
  }

  private OutboundEvent event(String routingKey, String aggregateId) {
    return new OutboundEvent(
        "ThingHappened",
        1,
        "Thing",
        aggregateId,
        routingKey,
        EventActor.system("test"),
        null,
        java.util.Map.of("value", 42));
  }

  private int attempts() {
    return jdbc.sql("SELECT max(attempts) FROM outbox_event").query(Integer.class).single();
  }

  private void makeDue() {
    jdbc.sql("UPDATE outbox_event SET next_attempt_at = now() - interval '1 second'").update();
  }

  private long pending() {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_event WHERE published_at IS NULL AND parked_at IS NULL")
        .query(Long.class)
        .single();
  }

  @Test
  void eventIsDeliveredAndOutboxRowMarkedPublished() {
    CorrelationContext.set("corr-123");
    tx.executeWithoutResult(s -> publisher.publish(event(ROUTING_KEY)));
    assertThat(pending()).isEqualTo(1);

    relay.relayPending();

    assertThat(pending()).isZero();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1));
    assertThat(
            jdbc.sql("SELECT payload->>'correlationId' FROM outbox_event")
                .query(String.class)
                .single())
        .isEqualTo("corr-123");
  }

  @Test
  void rolledBackTransactionLeavesNoEvent() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      publisher.publish(event(ROUTING_KEY));
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single()).isZero();
  }

  @Test
  void publishingOutsideATransactionIsRejected() {
    assertThatThrownBy(() -> publisher.publish(event(ROUTING_KEY)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unroutableEventStaysPendingAndIsCountedAsAttempt() {
    tx.executeWithoutResult(s -> publisher.publish(event("test.nobody-listens")));

    relay.relayPending();

    assertThat(pending()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT attempts FROM outbox_event").query(Integer.class).single())
        .isEqualTo(1);
    assertThat(jdbc.sql("SELECT last_error FROM outbox_event").query(String.class).single())
        .contains("No queue is bound");
  }

  @Test
  void retainedQueueKeepsEventsWithoutConsumerRoutable() {
    tx.executeWithoutResult(s -> publisher.publish(event(RETAINED_KEY)));

    relay.relayPending();

    assertThat(pending()).isZero();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(queueDepth(RETAINED_QUEUE)).isEqualTo(1));
  }

  @Test
  void failedEventIsNotRetriedBeforeItsBackoffExpires() {
    tx.executeWithoutResult(s -> publisher.publish(event("test.nobody-listens")));
    relay.relayPending();

    relay.relayPending();

    assertThat(attempts()).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT next_attempt_at > now() FROM outbox_event")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void failedEventIsPublishedOnceDueAndItsQueueExists() {
    tx.executeWithoutResult(s -> publisher.publish(event("test.late-queue")));
    relay.relayPending();
    assertThat(pending()).isEqualTo(1);

    Queue late = new Queue("test-service.late", true);
    admin.declareQueue(late);
    admin.declareBinding(
        BindingBuilder.bind(late)
            .to(new TopicExchange(messaging.exchange()))
            .with("test.late-queue"));
    makeDue();
    relay.relayPending();

    assertThat(pending()).isZero();
    admin.deleteQueue("test-service.late");
  }

  @Test
  void laterEventsOfTheSameAggregateWaitAcrossCycles() {
    tx.executeWithoutResult(s -> publisher.publish(event("test.nobody-listens", "thing-1")));
    relay.relayPending();

    tx.executeWithoutResult(
        s -> {
          publisher.publish(event(ROUTING_KEY, "thing-1"));
          publisher.publish(event(ROUTING_KEY, "thing-2"));
        });
    relay.relayPending();

    assertThat(pending()).isEqualTo(2); // thing-1: the failed event and the one behind it
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1)); // thing-2 is not held back
  }

  @Test
  void eventsInBackoffDoNotStarveTheBatch() {
    tx.executeWithoutResult(
        s -> {
          for (int i = 0; i < 5; i++) { // as many as the batch size
            publisher.publish(event("test.nobody-listens", "poison-" + i));
          }
        });
    relay.relayPending();
    assertThat(pending()).isEqualTo(5);

    tx.executeWithoutResult(s -> publisher.publish(event(ROUTING_KEY, "healthy")));
    relay.relayPending();

    assertThat(pending()).isEqualTo(5);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1));
  }

  @Test
  void unroutableEventIsParkedAfterMaxAttemptsAndStopsBlockingItsAggregate() {
    tx.executeWithoutResult(
        s -> {
          publisher.publish(event("test.nobody-listens", "thing-1"));
          publisher.publish(event(ROUTING_KEY, "thing-1"));
        });
    for (int i = 0; i < 3; i++) {
      relay.relayPending();
      makeDue();
    }
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE parked_at IS NOT NULL")
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(TestApp.EFFECTS).hasValue(0); // still held behind the failing event

    relay.relayPending();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1));
    assertThat(pending()).isZero();
    relay.relayPending();
    assertThat(attempts()).isEqualTo(3); // the parked event is no longer retried
  }

  @Test
  void brokerOutageNeverParksEventsAndCostsOneAttemptPerCycle() {
    CachingConnectionFactory unreachable = new CachingConnectionFactory("localhost", 1);
    unreachable.setConnectionTimeout(500);
    OutboxRelay offlineRelay =
        new OutboxRelay(
            jdbc,
            txManager,
            new RabbitTemplate(unreachable),
            messaging,
            new OutboxProperties(
                true,
                Duration.ofMillis(500),
                5,
                Duration.ofSeconds(1),
                Duration.ofDays(7),
                Duration.ofHours(1),
                1000,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                1),
            objectMapper,
            null,
            Clock.systemUTC());
    tx.executeWithoutResult(
        s -> {
          for (int i = 0; i < 3; i++) {
            publisher.publish(event(ROUTING_KEY, "thing-" + i));
          }
        });

    offlineRelay.relayPending();

    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE parked_at IS NOT NULL")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE attempts > 0")
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(pending()).isEqualTo(3);
    unreachable.destroy();
  }

  @Test
  void aFailedEventBlocksLaterEventsOfTheSameAggregateOnly() {
    tx.executeWithoutResult(
        s -> {
          publisher.publish(event("test.nobody-listens"));
          publisher.publish(event(ROUTING_KEY));
          publisher.publish(
              new OutboundEvent(
                  "ThingHappened",
                  1,
                  "Thing",
                  "thing-2",
                  ROUTING_KEY,
                  EventActor.system("test"),
                  null,
                  java.util.Map.of()));
        });

    relay.relayPending();

    assertThat(pending()).isEqualTo(2); // thing-1: failed + the one queued behind it
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1)); // thing-2 went through
  }

  @Test
  void redeliveredMessageProducesASingleEffectAndASingleRow() {
    tx.executeWithoutResult(s -> publisher.publish(event(ROUTING_KEY)));
    relay.relayPending();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(TestApp.EFFECTS).hasValue(1));

    Message duplicate =
        MessageBuilder.withBody(
                jdbc.sql("SELECT payload::text FROM outbox_event")
                    .query(String.class)
                    .single()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    rabbitTemplate.send(messaging.exchange(), ROUTING_KEY, duplicate);
    rabbitTemplate.send(messaging.exchange(), ROUTING_KEY, duplicate);

    await().atMost(Duration.ofSeconds(5)).until(() -> queueDepth(QUEUE) == 0);
    assertThat(TestApp.EFFECTS).hasValue(1);
    assertThat(jdbc.sql("SELECT count(*) FROM processed_message").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  void invalidJsonGoesToTheDeadLetterQueueWithoutBlockingTheNext() {
    rabbitTemplate.send(
        messaging.exchange(),
        ROUTING_KEY,
        MessageBuilder.withBody("{not json".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build());
    tx.executeWithoutResult(s -> publisher.publish(event(ROUTING_KEY)));
    relay.relayPending();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(queueDepth(ConsumerQueues.deadLetterQueueName(QUEUE))).isEqualTo(1);
              assertThat(TestApp.EFFECTS).hasValue(1);
            });
  }

  @Test
  void effectFailingBeforeCommitLeavesNoDedupRowSoRedeliveryRetriesIt() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                inbox.runOnce(
                    id,
                    "c",
                    () -> {
                      throw new IllegalStateException("fails before commit");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.sql("SELECT count(*) FROM processed_message").query(Long.class).single())
        .isZero();

    assertThat(inbox.runOnce(id, "c", () -> {})).isTrue();
    assertThat(inbox.runOnce(id, "c", () -> {})).isFalse();
  }

  private int queueDepth(String queue) {
    var props = admin.getQueueProperties(queue);
    return props == null ? -1 : ((Number) props.get("QUEUE_MESSAGE_COUNT")).intValue();
  }
}
