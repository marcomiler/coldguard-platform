package com.coldguard.gateway.infrastructure;

/**
 * Thrown when the call to Incident Service fails for a reason other than a duplicate incident
 * or an invalid request (for example, an internal or unavailable status).
 */
public class IncidentServiceException extends RuntimeException {

    public IncidentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
