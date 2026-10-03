package com.coldguard.commons.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Physical shape of every event on the wire (contracts/events/envelope.v1.schema.json). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String producer,
    String aggregateType,
    String aggregateId,
    String correlationId,
    EventActor actor,
    JsonNode payload) {}
