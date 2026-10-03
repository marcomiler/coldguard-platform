package com.coldguard.commons.messaging;

import com.coldguard.commons.messaging.error.PermanentMessageException;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes and parses envelopes. Parsing failures and unsupported versions are permanent errors:
 * retrying the same bytes cannot succeed, so they go straight to the dead-letter queue.
 */
public class EnvelopeCodec {

  private final ObjectMapper mapper;

  public EnvelopeCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String write(EventEnvelope envelope) {
    return mapper.writeValueAsString(envelope);
  }

  public EventEnvelope read(byte[] body) {
    EventEnvelope envelope;
    try {
      envelope = mapper.readValue(body, EventEnvelope.class);
    } catch (JacksonException e) {
      throw new PermanentMessageException("Message body is not a valid event envelope", e);
    }
    if (envelope == null
        || envelope.eventId() == null
        || envelope.eventType() == null
        || envelope.payload() == null) {
      throw new PermanentMessageException("Event envelope is missing mandatory fields");
    }
    return envelope;
  }

  /**
   * Parses the envelope and checks its {@code eventVersion} against what this consumer supports,
   * keyed by event type.
   */
  public EventEnvelope read(byte[] body, Map<String, Set<Integer>> supportedVersions) {
    EventEnvelope envelope = read(body);
    Set<Integer> supported = supportedVersions.get(envelope.eventType());
    if (supported == null || !supported.contains(envelope.eventVersion())) {
      throw new PermanentMessageException(
          "Unsupported event %s v%d".formatted(envelope.eventType(), envelope.eventVersion()));
    }
    return envelope;
  }
}
