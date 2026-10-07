package com.coldguard.gateway.api.incident;

import java.time.Instant;

/** An incident as the frontend sees it (contracts/rest/openapi.yaml, schema Incident). */
public record IncidentResponse(
    String id,
    String status,
    String assetId,
    String sensorId,
    String anomalyType,
    String impact,
    String urgency,
    String priority,
    Instant createdAt,
    Instant ackDueAt,
    Instant resolveDueAt,
    Instant acknowledgedAt,
    String acknowledgedBy,
    Instant lastEscalatedAt,
    int escalationCount,
    Instant closedAt,
    String closedBy,
    String cause,
    String resolutionComment,
    int occurrenceCount,
    Instant lastOccurrenceAt) {}
