package com.coldguard.gateway.api.audit;

import java.time.Instant;
import java.util.Map;

public record AuditRecordResponse(
    String id,
    Instant occurredAt,
    String sourceService,
    String entityType,
    String entityId,
    String action,
    String actorType,
    String actorId,
    String reason,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    String correlationId) {}
