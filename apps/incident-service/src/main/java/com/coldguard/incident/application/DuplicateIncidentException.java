package com.coldguard.incident.application;

public class DuplicateIncidentException extends RuntimeException {

    public DuplicateIncidentException(String message, Throwable cause) {
        super(message, cause);
    }
}
