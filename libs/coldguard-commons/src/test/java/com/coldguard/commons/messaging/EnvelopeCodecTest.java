package com.coldguard.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.messaging.error.PermanentMessageException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EnvelopeCodecTest {

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final EnvelopeCodec codec = new EnvelopeCodec(mapper);

  private byte[] bytes(String json) {
    return json.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void roundTripsAnEnvelope() {
    EventEnvelope envelope =
        new EventEnvelope(
            UUID.randomUUID(),
            "IncidentCreated",
            1,
            Instant.parse("2026-09-30T12:00:00Z"),
            "incident-service",
            "Incident",
            "inc-1",
            3L,
            "corr-1",
            EventActor.system("connectivity-monitor"),
            mapper.createObjectNode().put("priority", "P1"));

    EventEnvelope parsed = codec.read(bytes(codec.write(envelope)));

    assertThat(parsed).isEqualTo(envelope);
  }

  @Test
  void omitsAbsentCorrelationId() {
    EventEnvelope envelope =
        new EventEnvelope(
            UUID.randomUUID(),
            "IncidentCreated",
            1,
            Instant.now(),
            "incident-service",
            "Incident",
            "inc-1",
            null,
            null,
            EventActor.user("u-1"),
            mapper.createObjectNode());

    assertThat(codec.write(envelope))
        .doesNotContain("correlationId")
        .doesNotContain("aggregateVersion");
  }

  @Test
  void invalidJsonIsPermanent() {
    assertThatThrownBy(() -> codec.read(bytes("{not json")))
        .isInstanceOf(PermanentMessageException.class);
  }

  @Test
  void missingMandatoryFieldsArePermanent() {
    assertThatThrownBy(() -> codec.read(bytes("{\"eventType\":\"X\"}")))
        .isInstanceOf(PermanentMessageException.class);
  }

  @Test
  void unsupportedVersionIsPermanent() {
    String json =
        "{\"eventId\":\"%s\",\"eventType\":\"IncidentCreated\",\"eventVersion\":2,\"payload\":{}}"
            .formatted(UUID.randomUUID());

    assertThatThrownBy(() -> codec.read(bytes(json), Map.of("IncidentCreated", Set.of(1))))
        .isInstanceOf(PermanentMessageException.class)
        .hasMessageContaining("IncidentCreated v2");
  }

  @Test
  void unknownEventTypeIsPermanent() {
    String json =
        "{\"eventId\":\"%s\",\"eventType\":\"Other\",\"eventVersion\":1,\"payload\":{}}"
            .formatted(UUID.randomUUID());

    assertThatThrownBy(() -> codec.read(bytes(json), Map.of("IncidentCreated", Set.of(1))))
        .isInstanceOf(PermanentMessageException.class);
  }
}
