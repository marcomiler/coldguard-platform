package com.coldguard.commons.messaging.outbox;

import com.coldguard.commons.messaging.EventActor;
import java.time.Instant;

/**
 * What a service hands to {@link DomainEventPublisher}; the writer completes it into an envelope
 * (event id, producer, correlation id). {@code payload} is any Jackson-serializable object.
 */
public record OutboundEvent(
    String eventType,
    int eventVersion,
    String aggregateType,
    String aggregateId,
    String routingKey,
    EventActor actor,
    Instant occurredAt,
    Object payload) {}
