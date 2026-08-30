package com.coldguard.incident.application;

/**
 * Thrown by an {@link IncidentRepository} implementation when a save is rejected by a
 * persistence-level uniqueness constraint.
 */
public class DuplicateIncidentException extends RuntimeException {

    public DuplicateIncidentException(String message, Throwable cause) {
        super(message, cause);
    }
}
