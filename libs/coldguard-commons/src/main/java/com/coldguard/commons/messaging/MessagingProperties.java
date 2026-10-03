package com.coldguard.commons.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Broker topology names, kept in one place so no service repeats them as literals. */
@ConfigurationProperties(prefix = "coldguard.messaging")
public record MessagingProperties(
    @DefaultValue("coldguard.events") String exchange,
    @DefaultValue("coldguard.events.dlx") String deadLetterExchange,
    @DefaultValue("quorum") String queueType) {}
