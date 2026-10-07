package com.coldguard.incident.auditlog.application;

import java.time.Duration;

public record AuditQueryLimits(Duration maxRange, int defaultPageSize, int maxPageSize) {}
