package com.coldguard.gateway.infrastructure;

public class IncidentCloseForbiddenException extends IncidentServiceException {

    public IncidentCloseForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
