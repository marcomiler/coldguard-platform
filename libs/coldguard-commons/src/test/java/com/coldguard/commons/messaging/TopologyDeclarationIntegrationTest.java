package com.coldguard.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * A service with no listener and nothing to publish still has its queues on the broker once it has
 * started, so events other services publish for it are routable from the first moment.
 */
@Testcontainers
@SpringBootTest(
    classes = TopologyDeclarationIntegrationTest.App.class,
    properties = {
      "spring.application.name=topology-service",
      "spring.main.web-application-type=none",
      "coldguard.outbox.poll-interval=1h"
    })
class TopologyDeclarationIntegrationTest {

  static final String QUEUE = "topology-service.events";

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
  static class App {
    @Bean
    Declarables queues(MessagingProperties props) {
      return ConsumerQueues.declare(props, QUEUE, "other.thing-happened");
    }
  }

  /**
   * Asks the broker itself, over its management API. Going through Spring's AmqpAdmin would open a
   * connection, and opening one is exactly what makes Spring declare everything, so it could not
   * tell a service that declared at startup from one that merely did so because it was asked.
   */
  private static int queueStatus(String queue) throws Exception {
    String credentials =
        Base64.getEncoder().encodeToString("guest:guest".getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(rabbit.getHttpUrl() + "/api/queues/%2F/" + queue))
            .header("Authorization", "Basic " + credentials)
            .build();
    return HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  @Test
  void theQueuesAndTheirDeadLetterQueuesExistWithoutAnyListenerOrPublish() throws Exception {
    assertThat(queueStatus(QUEUE)).as("queue").isEqualTo(200);
    assertThat(queueStatus(ConsumerQueues.deadLetterQueueName(QUEUE)))
        .as("dead-letter queue")
        .isEqualTo(200);
  }
}
