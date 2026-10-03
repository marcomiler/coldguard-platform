package com.coldguard.commons.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.outbox")
public record OutboxProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("500ms") Duration pollInterval,
    @DefaultValue("100") int batchSize,
    @DefaultValue("5s") Duration confirmTimeout,
    @DefaultValue("7d") Duration retention,
    @DefaultValue("1h") Duration cleanupInterval,
    @DefaultValue("1000") int cleanupBatchSize,
    @DefaultValue("1s") Duration retryBaseDelay,
    @DefaultValue("1m") Duration retryMaxDelay,
    /*
     * Unroutable publishes (no queue bound for the routing key) are retried this many times and
     * then parked: the row stops being retried and stops blocking later events of its aggregate.
     * Broker outages never park a row.
     */
    @DefaultValue("20") int maxAttempts) {}
