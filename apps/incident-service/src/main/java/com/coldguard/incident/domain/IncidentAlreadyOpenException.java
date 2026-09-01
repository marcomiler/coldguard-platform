package com.coldguard.incident.domain;

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
