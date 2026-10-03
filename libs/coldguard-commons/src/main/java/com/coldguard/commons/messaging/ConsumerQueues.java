package com.coldguard.commons.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/** Builds a consumer's durable queue, its dead-letter queue and the bindings for its events. */
public final class ConsumerQueues {

  private ConsumerQueues() {}

  public static String deadLetterQueueName(String queueName) {
    return queueName + ".dlq";
  }

  /**
   * A bounded queue that keeps events nobody processes yet, so their routing keys stay routable (a
   * mandatory publish with no bound queue is a failure). Old or excess messages are dropped; it has
   * no dead-letter queue because nothing consumes it.
   */
  public static Declarables declareRetained(
      MessagingProperties props,
      String queueName,
      Duration retention,
      int maxLength,
      String... routingKeys) {
    Queue queue =
        QueueBuilder.durable(queueName)
            .withArgument("x-queue-type", props.queueType())
            .ttl((int) retention.toMillis())
            .maxLength(maxLength)
            .overflow(QueueBuilder.Overflow.dropHead)
            .build();
    TopicExchange events = new TopicExchange(props.exchange(), true, false);
    List<Declarable> declarables = new ArrayList<>(List.of(queue));
    for (String key : routingKeys) {
      declarables.add(BindingBuilder.bind(queue).to(events).with(key));
    }
    return new Declarables(declarables);
  }

  public static Declarables declare(
      MessagingProperties props, String queueName, String... routingKeys) {
    String dlq = deadLetterQueueName(queueName);
    Queue queue =
        QueueBuilder.durable(queueName)
            .withArgument("x-queue-type", props.queueType())
            .deadLetterExchange(props.deadLetterExchange())
            .deadLetterRoutingKey(dlq)
            .build();
    Queue deadLetters =
        QueueBuilder.durable(dlq).withArgument("x-queue-type", props.queueType()).build();

    List<Declarable> declarables = new ArrayList<>(List.of(queue, deadLetters));
    TopicExchange events = new TopicExchange(props.exchange(), true, false);
    TopicExchange dlx = new TopicExchange(props.deadLetterExchange(), true, false);
    declarables.add(BindingBuilder.bind(deadLetters).to(dlx).with(dlq));
    for (String key : routingKeys) {
      Binding binding = BindingBuilder.bind(queue).to(events).with(key);
      declarables.add(binding);
    }
    return new Declarables(declarables);
  }
}
