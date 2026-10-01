package com.coldguard.gateway.api;

import com.coldguard.incident.grpc.v1.Impact;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import com.coldguard.incident.grpc.v1.Priority;
import com.coldguard.incident.grpc.v1.Urgency;

public record CreateIncidentHttpResponse(
    String incidentId,
    IncidentStatus status,
    Impact impact,
    Urgency urgency,
    Priority priority,
    String createdAt) {}
