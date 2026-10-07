package com.coldguard.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * With no mail server reachable the request is retried; once retries run out the notification is
 * marked failed, {@code NotificationFailed} reaches the outbox and the message ends in the DLQ.
 */
@Testcontainers
@SpringBootTest
class NotificationRetriesExhaustedIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "notification");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
    // Nothing listens here.
    registry.add("spring.mail.host", () -> "localhost");
    registry.add("spring.mail.port", () -> "1");
    registry.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> "500");
    registry.add("spring.rabbitmq.listener.simple.retry.max-retries", () -> "3");
    registry.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "100ms");
  }

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired JdbcClient jdbc;

  @Test
  void exhaustedRetriesMarkTheNotificationFailedPublishTheFailureAndDeadLetter() {
    UUID requestId = UUID.randomUUID();
    String incident = UUID.randomUUID().toString();
    String body =
        """
        {"eventId":"%s","eventType":"NotificationRequested","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"incident-service",
         "aggregateType":"Incident","aggregateId":"%s","aggregateVersion":1,
         "actor":{"type":"SYSTEM","id":"incident-service"},
         "payload":{"notificationRequestId":"%s","incidentId":"%s",
           "notificationType":"INCIDENT_ESCALATED","priority":"P2","assetId":"a","sensorId":"s",
           "recipients":[{"userId":"u1","email":"u1@coldguard.test"}]}}
        """
            .formatted(UUID.randomUUID(), incident, requestId, incident);
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    rabbitTemplate.send(
        "coldguard.events",
        "incident.notification-requested",
        MessageBuilder.withBody(body.getBytes()).andProperties(properties).build());

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.sql(
                                "SELECT status FROM notification WHERE notification_request_id = ?")
                            .param(requestId)
                            .query(String.class)
                            .optional())
                    .contains("FAILED"));
    assertThat(
            jdbc.sql(
                    "SELECT payload::text FROM outbox_event WHERE event_type = 'NotificationFailed'"
                        + " AND aggregate_id = ?")
                .param(incident)
                .query(String.class)
                .single())
        .contains("RETRIES_EXHAUSTED", requestId.toString());
    assertThat(
            jdbc.sql(
                    "SELECT attempts FROM notification_delivery d JOIN notification n ON n.id ="
                        + " d.notification_id WHERE n.notification_request_id = ?")
                .param(requestId)
                .query(Integer.class)
                .single())
        .isGreaterThanOrEqualTo(2);
    Message dead = null;
    for (int i = 0; i < 20 && dead == null; i++) {
      dead = rabbitTemplate.receive("notification-service.notification-requested.dlq", 500);
    }
    assertThat(dead).isNotNull();
  }
}
