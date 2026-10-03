package com.coldguard.commons.messaging.inbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coldguard.inbox")
public record InboxProperties(
    @DefaultValue("7d") Duration retention,
    @DefaultValue("1h") Duration cleanupInterval,
    @DefaultValue("1000") int cleanupBatchSize) {}
