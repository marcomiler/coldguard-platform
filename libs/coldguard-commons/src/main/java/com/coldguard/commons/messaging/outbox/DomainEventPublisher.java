package com.coldguard.commons.messaging.outbox;

/**
 * Application-layer port for emitting domain events. The implementation must join the caller's
 * transaction so the event commits or rolls back together with the state change.
 */
public interface DomainEventPublisher {

  /**
   * @throws IllegalStateException if called outside an active transaction
   */
  void publish(OutboundEvent event);
}
