package com.coldguard.gateway.infrastructure;

/**
 * Thrown when Incident Service rejects a creation request because an open incident already
 * exists for the same asset/sensor/anomaly type.
 */
public class IncidentAlreadyExistsException extends IncidentServiceException {

    public IncidentAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
