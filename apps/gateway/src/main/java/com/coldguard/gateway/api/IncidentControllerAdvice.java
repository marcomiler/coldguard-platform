package com.coldguard.gateway.api;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.infrastructure.IncidentAlreadyClosedException;
import com.coldguard.gateway.infrastructure.IncidentAlreadyExistsException;
import com.coldguard.gateway.infrastructure.IncidentCloseForbiddenException;
import com.coldguard.gateway.infrastructure.IncidentNotFoundException;
import com.coldguard.gateway.infrastructure.IncidentServiceException;
import com.coldguard.gateway.infrastructure.InvalidIncidentRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IncidentControllerAdvice {

  @ExceptionHandler(IncidentAlreadyExistsException.class)
  public ResponseEntity<ProblemDetail> handleAlreadyExists(IncidentAlreadyExistsException ex) {
    return error(HttpStatus.CONFLICT, "INCIDENT_ALREADY_EXISTS", ex.getMessage());
  }

  @ExceptionHandler(InvalidIncidentRequestException.class)
  public ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidIncidentRequestException ex) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_REQUEST", ex.getMessage());
  }

  @ExceptionHandler(IncidentNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(IncidentNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(IncidentAlreadyClosedException.class)
  public ResponseEntity<ProblemDetail> handleAlreadyClosed(IncidentAlreadyClosedException ex) {
    return error(HttpStatus.CONFLICT, "INCIDENT_ALREADY_CLOSED", ex.getMessage());
  }

  @ExceptionHandler(IncidentCloseForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleCloseForbidden(IncidentCloseForbiddenException ex) {
    return error(HttpStatus.FORBIDDEN, "INCIDENT_CLOSE_FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(IncidentServiceException.class)
  public ResponseEntity<ProblemDetail> handleServiceFailure(IncidentServiceException ex) {
    return error(HttpStatus.BAD_GATEWAY, "INCIDENT_SERVICE_UNAVAILABLE", ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedBody(HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY", "Malformed request body");
  }

  private static ResponseEntity<ProblemDetail> error(
      HttpStatus status, String code, String message) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setProperty("code", code);
    CorrelationContext.current().ifPresent(id -> problem.setProperty("correlationId", id));
    return ResponseEntity.status(status).body(problem);
  }
}
