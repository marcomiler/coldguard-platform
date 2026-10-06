package com.coldguard.gateway.api;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.infrastructure.IdentityServiceException;
import com.coldguard.gateway.infrastructure.InvalidCredentialsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Login failures: always the same 401 body, whatever the cause. */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthControllerAdvice {

  @ExceptionHandler({InvalidCredentialsException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ProblemDetail> handleInvalidCredentials(Exception ex) {
    return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
  }

  @ExceptionHandler(IdentityServiceException.class)
  public ResponseEntity<ProblemDetail> handleIdentityUnavailable(IdentityServiceException ex) {
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
