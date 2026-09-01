package com.coldguard.gateway.infrastructure;

public class IncidentAlreadyClosedException extends IncidentServiceException {

    public IncidentAlreadyClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
