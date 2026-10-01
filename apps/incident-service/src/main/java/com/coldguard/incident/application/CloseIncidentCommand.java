package com.coldguard.incident.application;

public record CloseIncidentCommand(
    String incidentId, String cause, String resolutionComment, String actorRole) {}
