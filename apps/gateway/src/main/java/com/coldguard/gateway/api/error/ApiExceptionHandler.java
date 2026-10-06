package com.coldguard.gateway.api.error;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Problem Details (RFC 9457) for the resources that follow the shared API conventions. Every answer
 * carries a stable {@code code} and the {@code correlationId}; request values are never echoed. Add
 * a package to {@code basePackages} when another resource adopts these conventions.
 */
@RestControllerAdvice(
    basePackages = {"com.coldguard.gateway.api.asset", "com.coldguard.gateway.api.telemetry"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(DownstreamCallException.class)
  public ResponseEntity<ProblemDetail> handleDownstream(DownstreamCallException e) {
    GrpcStatusHttpMapper.Mapped mapped = GrpcStatusHttpMapper.map(e);
    if (mapped.logAsError()) {
      log.error(
          "Downstream call to {} failed with {} (correlationId={})",
          e.service(),
          e.grpcCode(),
          CorrelationContext.current().orElse("-"),
          e);
    }
    return problem(mapped.status(), mapped.code(), mapped.detail(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException e) {
    List<FieldProblem> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
            .toList();
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid", errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedBody(HttpMessageNotReadableException e) {
    return problem(
        HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY", "Malformed request body", null);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParameter(
      MissingServletRequestParameterException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "The parameter '" + e.getParameterName() + "' is required",
        null);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleBadParameter(MethodArgumentTypeMismatchException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "The parameter '" + e.getName() + "' is not valid",
        null);
  }

  /** A field that failed shape validation and why; never the value that was sent. */
  public record FieldProblem(String field, String message) {}

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail, List<FieldProblem> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    if (errors != null) {
      problem.setProperty("errors", errors);
    }
    CorrelationContext.current().ifPresent(id -> problem.setProperty("correlationId", id));
    return ResponseEntity.status(status).body(problem);
  }
}
