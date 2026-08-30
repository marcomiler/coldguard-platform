package com.coldguard.gateway.infrastructure;

/**
 * Thrown when Incident Service rejects a creation request as invalid.
 */
public class InvalidIncidentRequestException extends IncidentServiceException {

    public InvalidIncidentRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
