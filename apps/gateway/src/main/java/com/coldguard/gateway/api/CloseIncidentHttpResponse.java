package com.coldguard.gateway.api;

import com.coldguard.incident.grpc.v1.IncidentStatus;

public record CloseIncidentHttpResponse(
    String incidentId, IncidentStatus status, String closedAt) {}
