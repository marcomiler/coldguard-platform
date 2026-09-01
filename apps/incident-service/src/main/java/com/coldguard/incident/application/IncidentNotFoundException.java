package com.coldguard.incident.application;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String incidentId) {
        super("Incident not found: " + incidentId);
    }
}
