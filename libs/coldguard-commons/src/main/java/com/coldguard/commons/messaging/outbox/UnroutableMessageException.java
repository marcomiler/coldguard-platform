package com.coldguard.commons.messaging.outbox;

/** The broker returned the message because no queue is bound for its routing key. */
class UnroutableMessageException extends RuntimeException {

  UnroutableMessageException(String routingKey) {
    super("No queue is bound for routing key " + routingKey);
  }
}
