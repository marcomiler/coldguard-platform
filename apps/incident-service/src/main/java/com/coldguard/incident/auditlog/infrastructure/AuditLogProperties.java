package com.coldguard.incident.auditlog.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("coldguard.auditlog.query")
record AuditLogProperties(
    @DefaultValue("31d") Duration maxRange,
    @DefaultValue("50") int defaultPageSize,
    @DefaultValue("200") int maxPageSize) {}
