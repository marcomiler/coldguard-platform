package com.coldguard.incident.domain;

/**
 * Thrown when a creation request matches the asset/sensor/anomaly-type combination of an
 * incident that is already open.
 */
public class IncidentAlreadyOpenException extends RuntimeException {

    private final String existingIncidentId;

    public IncidentAlreadyOpenException(String existingIncidentId) {
        super("An open incident already exists: " + existingIncidentId);
        this.existingIncidentId = existingIncidentId;
    }

    public String getExistingIncidentId() {
        return existingIncidentId;
    }
}
