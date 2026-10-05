package com.coldguard.commons.messaging.inbox;

import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.error.OutOfOrderEventException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotent-consumer guard. The dedup row and the business effect commit in one transaction, so a
 * message that fails before commit leaves no trace and is redelivered; the listener returns (and
 * the container acks) only after that commit.
 */
public class InboxGuard {

  private static final Logger log = LoggerFactory.getLogger(InboxGuard.class);

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final InboxProperties props;
  private final Clock clock;

  public InboxGuard(
      JdbcClient jdbc, PlatformTransactionManager txManager, InboxProperties props, Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.props = props;
    this.clock = clock;
  }

  /**
   * Runs {@code effect} in a new transaction unless {@code messageId} was already processed by
   * {@code consumer}.
   *
   * @return {@code true} if the effect ran, {@code false} if the message was a duplicate
   */
  public boolean runOnce(UUID messageId, String consumer, Runnable effect) {
    return Boolean.TRUE.equals(
        tx.execute(
            status -> {
              if (!claim(messageId, consumer)) {
                return false;
              }
              effect.run();
              return true;
            }));
  }

  /**
   * Like {@link #runOnce} for consumers whose state depends on the order of an aggregate's events.
   * The effect runs only for the next expected {@code aggregateVersion}; an already seen version is
   * dropped as stale, and a version beyond the next one throws {@link OutOfOrderEventException},
   * which rolls back the claim so the delivery is retried. Envelopes without a version are not
   * sequenced and behave as in {@link #runOnce}.
   *
   * @return {@code true} if the effect ran, {@code false} for a duplicate or stale event
   */
  public boolean runInOrder(EventEnvelope envelope, String consumer, Runnable effect) {
    if (envelope.aggregateVersion() == null) {
      return runOnce(envelope.eventId(), consumer, effect);
    }
    return Boolean.TRUE.equals(
        tx.execute(
            status -> {
              if (!claim(envelope.eventId(), consumer)) {
                return false;
              }
              long last = lockCursor(envelope, consumer);
              long version = envelope.aggregateVersion();
              if (version <= last) {
                return false;
              }
              if (version > last + 1) {
                throw new OutOfOrderEventException(
                    envelope.aggregateType(), envelope.aggregateId(), last + 1, version);
              }
              effect.run();
              jdbc.sql(
                      """
                      UPDATE aggregate_cursor SET last_version = ?
                       WHERE consumer = ? AND aggregate_type = ? AND aggregate_id = ?
                      """)
                  .params(version, consumer, envelope.aggregateType(), envelope.aggregateId())
                  .update();
              return true;
            }));
  }

  /** Creates the cursor if missing and locks it, so events of one aggregate apply one at a time. */
  private long lockCursor(EventEnvelope envelope, String consumer) {
    jdbc.sql(
            """
            INSERT INTO aggregate_cursor (consumer, aggregate_type, aggregate_id, last_version)
            VALUES (?, ?, ?, 0) ON CONFLICT DO NOTHING
            """)
        .params(consumer, envelope.aggregateType(), envelope.aggregateId())
        .update();
    return jdbc.sql(
            """
            SELECT last_version FROM aggregate_cursor
             WHERE consumer = ? AND aggregate_type = ? AND aggregate_id = ? FOR UPDATE
            """)
        .params(consumer, envelope.aggregateType(), envelope.aggregateId())
        .query(Long.class)
        .single();
  }

  /**
   * Records the message inside the current transaction.
   *
   * @return {@code false} if it was already recorded (duplicate delivery)
   */
  public boolean claim(UUID messageId, String consumer) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("InboxGuard.claim requires an active transaction");
    }
    return jdbc.sql(
                """
                INSERT INTO processed_message (message_id, consumer, processed_at)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """)
            .params(messageId, consumer, Timestamp.from(clock.instant()))
            .update()
        == 1;
  }

  @Scheduled(
      fixedDelayString = "${coldguard.inbox.cleanup-interval:1h}",
      initialDelayString = "${coldguard.inbox.cleanup-interval:1h}")
  public void purgeExpired() {
    Timestamp cutoff = Timestamp.from(clock.instant().minus(props.retention()));
    int deleted;
    try {
      do {
        deleted =
            jdbc.sql(
                    """
                    DELETE FROM processed_message WHERE ctid IN (
                      SELECT ctid FROM processed_message WHERE processed_at < ? LIMIT ?)
                    """)
                .params(cutoff, props.cleanupBatchSize())
                .update();
      } while (deleted >= props.cleanupBatchSize());
    } catch (RuntimeException e) {
      log.warn("Inbox cleanup failed: {}", e.getClass().getSimpleName());
    }
  }
}
