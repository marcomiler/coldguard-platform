package com.coldguard.commons.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Meters for the pending backlog and for publish outcomes, tagged by event type. */
public class OutboxMetrics {

  private final MeterRegistry registry;
  private final JdbcClient jdbc;

  public OutboxMetrics(MeterRegistry registry, JdbcClient jdbc) {
    this.registry = registry;
    this.jdbc = jdbc;
    Gauge.builder("coldguard.outbox.pending", this, OutboxMetrics::pending)
        .description("Outbox events not yet confirmed by the broker")
        .register(registry);
    Gauge.builder("coldguard.outbox.parked", this, OutboxMetrics::parked)
        .description("Outbox events parked after repeated unroutable publishes; need manual action")
        .register(registry);
    Gauge.builder("coldguard.outbox.oldest.age.seconds", this, OutboxMetrics::oldestAgeSeconds)
        .description("Age of the oldest unpublished outbox event")
        .baseUnit("seconds")
        .register(registry);
  }

  void published(String eventType) {
    Counter.builder("coldguard.outbox.published")
        .tag("event_type", eventType)
        .register(registry)
        .increment();
  }

  void failed(String eventType) {
    Counter.builder("coldguard.outbox.publish.failures")
        .tag("event_type", eventType)
        .register(registry)
        .increment();
  }

  private double pending() {
    return query(
        "SELECT count(*) FROM outbox_event WHERE published_at IS NULL AND parked_at IS NULL");
  }

  private double parked() {
    return query(
        "SELECT count(*) FROM outbox_event WHERE published_at IS NULL AND parked_at IS NOT NULL");
  }

  private double oldestAgeSeconds() {
    return query(
        "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0)"
            + " FROM outbox_event WHERE published_at IS NULL AND parked_at IS NULL");
  }

  // A scrape must never fail because the database is briefly unreachable.
  private double query(String sql) {
    try {
      return jdbc.sql(sql).query(Double.class).single();
    } catch (RuntimeException e) {
      return Double.NaN;
    }
  }
}
