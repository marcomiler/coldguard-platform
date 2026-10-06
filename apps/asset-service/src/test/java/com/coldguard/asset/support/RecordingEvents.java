package com.coldguard.asset.support;

import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import java.util.ArrayList;
import java.util.List;

/** Collects what a use case publishes, so a test can assert on type, routing key and payload. */
public class RecordingEvents implements DomainEventPublisher {

  public final List<OutboundEvent> published = new ArrayList<>();

  @Override
  public void publish(OutboundEvent event) {
    published.add(event);
  }

  public OutboundEvent only() {
    if (published.size() != 1) {
      throw new AssertionError("expected exactly one event but got " + published.size());
    }
    return published.get(0);
  }
}
