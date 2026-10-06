package com.coldguard.gateway.api;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.infrastructure.IdentityServiceException;
import com.coldguard.gateway.infrastructure.UserAdministrationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problem Details for user administration; never echoes request bodies. */
@RestControllerAdvice(assignableTypes = UserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserControllerAdvice {

  @ExceptionHandler(UserAdministrationException.class)
  public ResponseEntity<ProblemDetail> handleAdministration(UserAdministrationException ex) {
    return switch (ex.kind()) {
      case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "USER_ADMIN_FORBIDDEN", ex.getMessage());
      case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
      case ALREADY_EXISTS -> problem(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage());
      case CONFLICT -> problem(HttpStatus.CONFLICT, "USER_STATE_CONFLICT", ex.getMessage());
      case INVALID -> problem(HttpStatus.BAD_REQUEST, "INVALID_USER_REQUEST", ex.getMessage());
    };
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedBody(HttpMessageNotReadableException ex) {
    return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY", "Malformed request body");
  }

  @ExceptionHandler(IdentityServiceException.class)
  public ResponseEntity<ProblemDetail> handleUnavailable(IdentityServiceException ex) {
    return problem(
        HttpStatus.BAD_GATEWAY, "IDENTITY_SERVICE_UNAVAILABLE", "Identity service unavailable");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    CorrelationContext.current().ifPresent(id -> problem.setProperty("correlationId", id));
    return ResponseEntity.status(status).body(problem);
  }
}
