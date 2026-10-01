package com.coldguard.gateway.infrastructure;

public class IncidentNotFoundException extends IncidentServiceException {

  public IncidentNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
