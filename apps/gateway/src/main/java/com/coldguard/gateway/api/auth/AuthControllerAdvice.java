package com.coldguard.gateway.api.auth;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.api.error.GrpcStatusHttpMapper;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import com.coldguard.gateway.infrastructure.InvalidCredentialsException;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Login failures: always the same 401 body, whatever the cause (so the answer never says whether
 * the user exists). Infrastructure failures use the shared gRPC-to-HTTP translation.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthControllerAdvice {

  private static final Logger log = LoggerFactory.getLogger(AuthControllerAdvice.class);

  @ExceptionHandler({InvalidCredentialsException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ProblemDetail> handleInvalidCredentials(Exception ex) {
    return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
  }

  @ExceptionHandler(DownstreamCallException.class)
  public ResponseEntity<ProblemDetail> handleDownstream(DownstreamCallException ex) {
    if (ex.grpcCode() == Status.Code.UNAUTHENTICATED
        || ex.grpcCode() == Status.Code.PERMISSION_DENIED) {
      return handleInvalidCredentials(ex);
    }
    GrpcStatusHttpMapper.Mapped mapped = GrpcStatusHttpMapper.map(ex);
    if (mapped.logAsError()) {
      log.error("Identity call failed with {}", ex.grpcCode());
    }
    return problem(mapped.status(), mapped.code(), mapped.detail());
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    CorrelationContext.current().ifPresent(id -> problem.setProperty("correlationId", id));
    return ResponseEntity.status(status).body(problem);
  }
}
