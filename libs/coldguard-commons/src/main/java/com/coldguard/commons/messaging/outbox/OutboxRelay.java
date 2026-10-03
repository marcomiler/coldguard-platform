package com.coldguard.commons.messaging.outbox;

import com.coldguard.commons.messaging.MessagingHeaders;
import com.coldguard.commons.messaging.MessagingProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes pending outbox rows with publisher confirms. Each cycle runs in one short transaction
 * that locks its batch with {@code FOR UPDATE SKIP LOCKED}, so several instances never publish the
 * same row concurrently. A crash between broker confirm and commit republishes the row, which the
 * idempotent consumers absorb.
 */
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

  private static final String SELECT_PENDING =
      """
      SELECT o.id, o.aggregate_id, o.event_type, o.event_version, o.routing_key,
             o.payload::text AS payload, o.headers::text AS headers, o.attempts
        FROM outbox_event o
       WHERE o.published_at IS NULL
         AND o.parked_at IS NULL
         AND o.next_attempt_at <= ?
         AND NOT EXISTS (
               SELECT 1 FROM outbox_event earlier
                WHERE earlier.aggregate_type = o.aggregate_type
                  AND earlier.aggregate_id = o.aggregate_id
                  AND earlier.published_at IS NULL
                  AND earlier.parked_at IS NULL
                  AND earlier.next_attempt_at > ?
                  AND (earlier.created_at, earlier.id) < (o.created_at, o.id))
       ORDER BY o.created_at, o.id
       LIMIT ?
         FOR UPDATE OF o SKIP LOCKED
      """;

  private record PendingRow(
      UUID id,
      String aggregateId,
      String eventType,
      int eventVersion,
      String routingKey,
      String payload,
      String headers,
      int attempts) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RabbitTemplate rabbit;
  private final MessagingProperties messaging;
  private final OutboxProperties props;
  private final ObjectMapper mapper;
  private final OutboxMetrics metrics; // null when no MeterRegistry is present
  private final Clock clock;

  public OutboxRelay(
      JdbcClient jdbc,
      PlatformTransactionManager txManager,
      RabbitTemplate rabbit,
      MessagingProperties messaging,
      OutboxProperties props,
      ObjectMapper mapper,
      OutboxMetrics metrics,
      Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.rabbit = rabbit;
    this.messaging = messaging;
    this.props = props;
    this.mapper = mapper;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${coldguard.outbox.poll-interval:500ms}")
  public void relayPending() {
    try {
      tx.executeWithoutResult(status -> publishBatch());
    } catch (RuntimeException e) {
      // The database is unavailable or the cycle was rolled back; rows stay pending for the next
      // one.
      log.warn("Outbox relay cycle failed: {}", e.getClass().getSimpleName());
    }
  }

  private void publishBatch() {
    Timestamp now = Timestamp.from(clock.instant());
    List<PendingRow> rows =
        jdbc.sql(SELECT_PENDING)
            .params(now, now, props.batchSize())
            .query(
                (rs, n) ->
                    new PendingRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getString("routing_key"),
                        rs.getString("payload"),
                        rs.getString("headers"),
                        rs.getInt("attempts")))
            .list();

    // The query already holds back rows behind an earlier failed event of their aggregate; this
    // set covers failures that happen inside the current batch.
    Set<String> blockedAggregates = new HashSet<>();
    for (PendingRow row : rows) {
      if (blockedAggregates.contains(row.aggregateId())) {
        continue;
      }
      try {
        publish(row);
        jdbc.sql("UPDATE outbox_event SET published_at = ? WHERE id = ?")
            .params(Timestamp.from(clock.instant()), row.id())
            .update();
        if (metrics != null) {
          metrics.published(row.eventType());
        }
      } catch (UnroutableMessageException e) {
        recordFailure(row, e, true);
        blockedAggregates.add(row.aggregateId());
      } catch (RuntimeException | InterruptedException | ExecutionException | TimeoutException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        // Connection loss, timeout or nack say nothing about this message: stop the cycle so a
        // broker outage costs one failed attempt per cycle, not one per pending row.
        recordFailure(row, e, false);
        return;
      }
    }
  }

  private void publish(PendingRow row)
      throws InterruptedException, ExecutionException, TimeoutException {
    Message message = toMessage(row);
    CorrelationData correlation = new CorrelationData(row.id().toString());
    rabbit.send(messaging.exchange(), row.routingKey(), message, correlation);
    CorrelationData.Confirm confirm =
        correlation.getFuture().get(props.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
    if (!confirm.isAck()) {
      throw new IllegalStateException("Broker nacked the message: " + confirm.getReason());
    }
    if (correlation.getReturned() != null) {
      throw new UnroutableMessageException(row.routingKey());
    }
  }

  private Message toMessage(PendingRow row) {
    MessageBuilderSupport<Message> builder =
        MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
            .setMessageId(row.id().toString())
            .setType(row.eventType())
            .setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name())
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setHeader(MessagingHeaders.EVENT_VERSION, row.eventVersion());
    mapper.readValue(row.headers(), STRING_MAP).forEach(builder::setHeader);
    return builder.build();
  }

  private void recordFailure(PendingRow row, Exception e, boolean unroutable) {
    int attempts = row.attempts() + 1;
    Instant now = clock.instant();
    boolean park = unroutable && attempts >= props.maxAttempts();
    jdbc.sql(
            """
            UPDATE outbox_event
               SET attempts = ?, last_error = ?, next_attempt_at = ?, parked_at = ?
             WHERE id = ?
            """)
        .params(
            attempts,
            describe(e),
            Timestamp.from(now.plus(backoff(attempts))),
            park ? Timestamp.from(now) : null,
            row.id())
        .update();
    if (metrics != null) {
      metrics.failed(row.eventType());
    }
    if (park) {
      log.error(
          "Outbox event parked eventId={} eventType={} routingKey={} attempts={}: no queue is bound",
          row.id(),
          row.eventType(),
          row.routingKey(),
          attempts);
    } else {
      log.warn(
          "Outbox publish failed eventId={} eventType={} attempts={} error={}",
          row.id(),
          row.eventType(),
          attempts,
          e.getClass().getSimpleName());
    }
  }

  private Duration backoff(int attempts) {
    long factor = 1L << Math.min(attempts - 1, 20);
    Duration delay = props.retryBaseDelay().multipliedBy(factor);
    return delay.compareTo(props.retryMaxDelay()) > 0 ? props.retryMaxDelay() : delay;
  }

  /** Exception class plus a bounded message; never the payload. */
  static String describe(Exception e) {
    String text = e.getClass().getSimpleName() + ": " + e.getMessage();
    return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
  }

  /** Deletes published rows older than the retention window, in bounded batches. */
  @Scheduled(
      fixedDelayString = "${coldguard.outbox.cleanup-interval:1h}",
      initialDelayString = "${coldguard.outbox.cleanup-interval:1h}")
  public void purgePublished() {
    Timestamp cutoff = Timestamp.from(clock.instant().minus(props.retention()));
    int deleted;
    try {
      do {
        deleted =
            jdbc.sql(
                    """
                    DELETE FROM outbox_event WHERE id IN (
                      SELECT id FROM outbox_event
                       WHERE published_at IS NOT NULL AND published_at < ? LIMIT ?)
                    """)
                .params(cutoff, props.cleanupBatchSize())
                .update();
      } while (deleted >= props.cleanupBatchSize());
    } catch (RuntimeException e) {
      log.warn("Outbox cleanup failed: {}", e.getClass().getSimpleName());
    }
  }
}
