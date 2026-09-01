package com.coldguard.gateway.api;

import com.coldguard.gateway.infrastructure.IncidentAlreadyClosedException;
import com.coldguard.gateway.infrastructure.IncidentAlreadyExistsException;
import com.coldguard.gateway.infrastructure.IncidentCloseForbiddenException;
import com.coldguard.gateway.infrastructure.IncidentNotFoundException;
import com.coldguard.gateway.infrastructure.IncidentServiceException;
import com.coldguard.gateway.infrastructure.InvalidIncidentRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IncidentControllerAdvice {

    @ExceptionHandler(IncidentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(IncidentAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "INCIDENT_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InvalidIncidentRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidIncidentRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IncidentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IncidentAlreadyClosedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyClosed(IncidentAlreadyClosedException ex) {
        return error(HttpStatus.CONFLICT, "INCIDENT_ALREADY_CLOSED", ex.getMessage());
    }

    @ExceptionHandler(IncidentCloseForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleCloseForbidden(IncidentCloseForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, "INCIDENT_CLOSE_FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(IncidentServiceException.class)
    public ResponseEntity<ErrorResponse> handleServiceFailure(IncidentServiceException ex) {
        return error(HttpStatus.BAD_GATEWAY, "INCIDENT_SERVICE_UNAVAILABLE", ex.getMessage());
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
