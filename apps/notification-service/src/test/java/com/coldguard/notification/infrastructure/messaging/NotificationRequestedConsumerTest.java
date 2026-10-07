package com.coldguard.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.notification.application.NotificationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NotificationRequestedConsumerTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String ID = "00000000-0000-0000-0000-0000000000b1";

  private static EventEnvelope event(String payload) {
    return new EventEnvelope(
        UUID.randomUUID(),
        "NotificationRequested",
        1,
        Instant.now(),
        "incident-service",
        "Incident",
        ID,
        1L,
        null,
        EventActor.system("x"),
        JSON.readTree(payload));
  }

  private static String payload(String type, String recipients) {
    return """
        {"notificationRequestId":"%s","incidentId":"%s","notificationType":"%s","priority":"P2",
         "assetId":"a","sensorId":"s","recipients":%s}
        """
        .formatted(UUID.randomUUID(), ID, type, recipients);
  }

  @Test
  void readsAValidRequest() {
    NotificationRequest request =
        NotificationRequestedConsumer.toRequest(
            event(payload("INCIDENT_ESCALATED", "[{\"userId\":\"u1\",\"email\":\"u1@x.test\"}]")));

    assertThat(request.type()).isEqualTo("INCIDENT_ESCALATED");
    assertThat(request.priority()).isEqualTo("P2");
    assertThat(request.recipients()).hasSize(1);
    assertThat(request.incidentId()).isEqualTo(UUID.fromString(ID));
  }

  @Test
  void anUnknownTypeNoRecipientsOrABrokenIdIsPermanent() {
    assertThatThrownBy(
            () ->
                NotificationRequestedConsumer.toRequest(
                    event(payload("OTHER", "[{\"userId\":\"u\",\"email\":\"e@x.test\"}]"))))
        .isInstanceOf(PermanentMessageException.class);
    assertThatThrownBy(
            () -> NotificationRequestedConsumer.toRequest(event(payload("INCIDENT_CREATED", "[]"))))
        .isInstanceOf(PermanentMessageException.class);
    assertThatThrownBy(
            () ->
                NotificationRequestedConsumer.toRequest(
                    event(
                        "{\"notificationRequestId\":\"nope\",\"incidentId\":\""
                            + ID
                            + "\",\"notificationType\":\"INCIDENT_CREATED\",\"assetId\":\"a\","
                            + "\"sensorId\":\"s\",\"recipients\":[{\"userId\":\"u\",\"email\":\"e\"}]}")))
        .isInstanceOf(PermanentMessageException.class);
  }
}
