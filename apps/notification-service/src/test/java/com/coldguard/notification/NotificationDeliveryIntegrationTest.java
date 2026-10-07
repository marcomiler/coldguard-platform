package com.coldguard.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The delivery flow against real PostgreSQL, RabbitMQ and a real SMTP server (Mailpit): one email
 * per recipient, nothing more for a repeated request, no address stored, and a request that can
 * never be understood ends in the dead-letter queue.
 */
@Testcontainers
@SpringBootTest
class NotificationDeliveryIntegrationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @Container
  static GenericContainer<?> mailpit =
      new GenericContainer<>("axllent/mailpit:v1.21").withExposedPorts(1025, 8025);

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
    registry.add("spring.mail.host", mailpit::getHost);
    registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired JdbcClient jdbc;

  @Test
  void sendsOneEmailPerRecipient_aRepeatedRequestSendsNone_andNoAddressIsStored() throws Exception {
    UUID requestId = UUID.randomUUID();
    String incident = UUID.randomUUID().toString();
    String supervisorA = "sup-a-" + requestId + "@coldguard.test";
    String supervisorB = "sup-b-" + requestId + "@coldguard.test";

    publish(UUID.randomUUID(), requestId, incident, "INCIDENT_CREATED", supervisorA, supervisorB);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(mailsFor(requestId.toString())).hasSize(2));
    List<JsonNode> mails = mailsFor(requestId.toString());
    assertThat(mails)
        .allSatisfy(
            m -> {
              assertThat(m.path("To")).hasSize(1);
              assertThat(m.path("Subject").asString()).contains("P1");
              assertThat(m.path("Snippet").asString()).contains(incident);
            });
    assertThat(mails.stream().map(m -> m.path("To").get(0).path("Address").asString()))
        .containsExactlyInAnyOrder(supervisorA, supervisorB);
    assertThat(
            jdbc.sql("SELECT status FROM notification WHERE notification_request_id = ?")
                .param(requestId)
                .query(String.class)
                .single())
        .isEqualTo("SENT");

    // The same request again, as a new event: nothing more is sent.
    publish(UUID.randomUUID(), requestId, incident, "INCIDENT_CREATED", supervisorA, supervisorB);
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(8))
        .untilAsserted(() -> assertThat(mailsFor(requestId.toString())).hasSize(2));

    // Only who was notified is kept, never where.
    String stored =
        jdbc.sql(
                "SELECT string_agg(d::text, ' ') FROM notification_delivery d "
                    + "JOIN notification n ON n.id = d.notification_id "
                    + "WHERE n.notification_request_id = ?")
            .param(requestId)
            .query(String.class)
            .single();
    assertThat(stored).doesNotContain("coldguard.test").contains("SENT");
  }

  @Test
  void aRequestThatCanNeverBeUnderstoodIsDeadLettered() {
    String body =
        """
        {"eventId":"%s","eventType":"NotificationRequested","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"incident-service",
         "aggregateType":"Incident","aggregateId":"x","actor":{"type":"SYSTEM","id":"x"},
         "payload":{"notificationType":"UNKNOWN_TYPE"}}
        """
            .formatted(UUID.randomUUID());
    rabbitTemplate.send("coldguard.events", "incident.notification-requested", message(body));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        rabbitTemplate.receive(
                            "notification-service.notification-requested.dlq", 500))
                    .isNotNull());
  }

  private void publish(
      UUID eventId, UUID requestId, String incident, String type, String... emails) {
    StringBuilder recipients = new StringBuilder();
    for (int i = 0; i < emails.length; i++) {
      recipients
          .append(i == 0 ? "" : ",")
          .append("{\"userId\":\"user-")
          .append(i)
          .append("\",\"email\":\"")
          .append(emails[i])
          .append("\"}");
    }
    String body =
        """
        {"eventId":"%s","eventType":"NotificationRequested","eventVersion":1,
         "occurredAt":"2026-10-01T10:00:00Z","producer":"incident-service",
         "aggregateType":"Incident","aggregateId":"%s","aggregateVersion":1,
         "actor":{"type":"SYSTEM","id":"incident-service"},
         "payload":{"notificationRequestId":"%s","incidentId":"%s","notificationType":"%s",
           "priority":"P1","assetId":"asset-1","sensorId":"sensor-1","recipients":[%s]}}
        """
            .formatted(eventId, incident, requestId, incident, type, recipients);
    rabbitTemplate.send("coldguard.events", "incident.notification-requested", message(body));
  }

  private static Message message(String body) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    return MessageBuilder.withBody(body.getBytes()).andProperties(properties).build();
  }

  /** Messages Mailpit holds whose text mentions the given incident or request marker. */
  private List<JsonNode> mailsFor(String requestId) throws Exception {
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://%s:%d/api/v1/search?query=%s"
                            .formatted(
                                mailpit.getHost(), mailpit.getMappedPort(8025), "to:" + requestId)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    JsonNode messages = JSON.readTree(response.body()).path("messages");
    return messages.valueStream().toList();
  }
}
