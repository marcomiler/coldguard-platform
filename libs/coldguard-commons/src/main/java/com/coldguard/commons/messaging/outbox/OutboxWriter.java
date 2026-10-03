package com.coldguard.commons.messaging.outbox;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.MessagingHeaders;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Inserts the envelope into {@code outbox_event} inside the caller's transaction. */
public class OutboxWriter implements DomainEventPublisher {

  private static final String INSERT =
      """
      INSERT INTO outbox_event
        (id, aggregate_type, aggregate_id, event_type, event_version, routing_key,
         payload, headers, created_at, next_attempt_at)
      VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
      """;

  private final JdbcClient jdbc;
  private final EnvelopeCodec codec;
  private final ObjectMapper mapper;
  private final String producer;
  private final Clock clock;

  public OutboxWriter(
      JdbcClient jdbc, EnvelopeCodec codec, ObjectMapper mapper, String producer, Clock clock) {
    this.jdbc = jdbc;
    this.codec = codec;
    this.mapper = mapper;
    this.producer = producer;
    this.clock = clock;
  }

  @Override
  public void publish(OutboundEvent event) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Domain events must be published inside the use case transaction");
    }
    UUID eventId = UUID.randomUUID();
    Instant now = clock.instant();
    String correlationId = CorrelationContext.current().orElse(null);
    EventEnvelope envelope =
        new EventEnvelope(
            eventId,
            event.eventType(),
            event.eventVersion(),
            event.occurredAt() != null ? event.occurredAt() : now,
            producer,
            event.aggregateType(),
            event.aggregateId(),
            correlationId,
            event.actor(),
            mapper.valueToTree(event.payload()));

    Map<String, String> headers = new LinkedHashMap<>();
    if (correlationId != null) {
      headers.put(MessagingHeaders.CORRELATION_ID, correlationId);
    }
    String traceparent = currentTraceparent();
    if (traceparent != null) {
      headers.put(MessagingHeaders.TRACEPARENT, traceparent);
    }

    jdbc.sql(INSERT)
        .params(
            eventId,
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.eventVersion(),
            event.routingKey(),
            codec.write(envelope),
            mapper.writeValueAsString(headers),
            Timestamp.from(now),
            Timestamp.from(now))
        .update();
  }

  /** W3C trace context built from the tracing MDC entries when a tracer is active. */
  private static String currentTraceparent() {
    String traceId = MDC.get("traceId");
    String spanId = MDC.get("spanId");
    if (traceId == null || spanId == null || traceId.length() != 32 || spanId.length() != 16) {
      return null;
    }
    return "00-" + traceId + "-" + spanId + "-01";
  }
}
