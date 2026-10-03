package com.coldguard.commons.messaging.error;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * A message that can never be processed successfully (malformed, unsupported version, impossible
 * business reference). It is excluded from listener retries and rejected without requeue, so the
 * broker dead-letters it.
 */
public class PermanentMessageException extends AmqpRejectAndDontRequeueException {

  public PermanentMessageException(String message) {
    super(message);
  }

  public PermanentMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
