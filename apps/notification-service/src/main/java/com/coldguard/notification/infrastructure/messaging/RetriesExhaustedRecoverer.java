package com.coldguard.notification.infrastructure.messaging;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.error.PermanentMessageException;
import com.coldguard.notification.application.NotificationLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.stereotype.Component;

/**
 * Runs when the broker has redelivered a request as often as allowed. Before the message is
 * dead-lettered, the recipients still pending are marked failed and {@code NotificationFailed} is
 * written to the outbox, so the loss is visible and not only a message in a queue. A message that
 * was never understood only gets dead-lettered.
 */
@Component
class RetriesExhaustedRecoverer implements MessageRecoverer {

  private static final Logger log = LoggerFactory.getLogger(RetriesExhaustedRecoverer.class);

  private static final MessageRecoverer REJECT = new RejectAndDontRequeueRecoverer();

  private final EnvelopeCodec codec;
  private final NotificationLedger ledger;

  RetriesExhaustedRecoverer(EnvelopeCodec codec, NotificationLedger ledger) {
    this.codec = codec;
    this.ledger = ledger;
  }

  @Override
  public void recover(Message message, Throwable cause) {
    if (!isPermanent(cause)) {
      try {
        var request = NotificationRequestedConsumer.toRequest(codec.read(message.getBody()));
        ledger.recordRetriesExhausted(request.requestId());
        log.error(
            "Notification {} for incident {} gave up after retries",
            request.requestId(),
            request.incidentId());
      } catch (RuntimeException e) {
        log.error("Could not record the exhausted notification: {}", e.getClass().getSimpleName());
      }
    }
    // Rejects without requeue, so the broker dead-letters the message.
    REJECT.recover(message, cause);
  }

  private static boolean isPermanent(Throwable cause) {
    for (Throwable t = cause; t != null; t = t.getCause()) {
      if (t instanceof PermanentMessageException) {
        return true;
      }
    }
    return false;
  }
}
