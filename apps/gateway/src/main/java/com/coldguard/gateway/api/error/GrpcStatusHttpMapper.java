package com.coldguard.gateway.api.error;

import com.coldguard.gateway.infrastructure.DownstreamCallException;
import org.springframework.http.HttpStatus;

/**
 * The one translation from a downstream gRPC failure to an HTTP answer. The business code a service
 * published travels untouched; the Gateway never interprets a message. What a service describes for
 * an internal error is never forwarded: it may reveal details.
 */
public final class GrpcStatusHttpMapper {

  /** What the client is told. */
  public record Mapped(HttpStatus status, String code, String detail, boolean logAsError) {}

  private static final String INTERNAL_DETAIL = "The service could not complete the request";

  private GrpcStatusHttpMapper() {}

  public static Mapped map(DownstreamCallException e) {
    String published = e.errorCode();
    return switch (e.grpcCode()) {
      case INVALID_ARGUMENT -> business(HttpStatus.BAD_REQUEST, published, "INVALID_REQUEST", e);
      case UNAUTHENTICATED ->
          new Mapped(
              HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required", false);
      case PERMISSION_DENIED ->
          new Mapped(HttpStatus.FORBIDDEN, "FORBIDDEN", "The action is not allowed", false);
      case NOT_FOUND -> business(HttpStatus.NOT_FOUND, published, "NOT_FOUND", e);
      case ALREADY_EXISTS -> business(HttpStatus.CONFLICT, published, "ALREADY_EXISTS", e);
      case FAILED_PRECONDITION ->
          business(HttpStatus.CONFLICT, published, "FAILED_PRECONDITION", e);
      case ABORTED ->
          new Mapped(
              HttpStatus.CONFLICT,
              "CONCURRENT_MODIFICATION",
              "The resource was modified by someone else; reload it and retry",
              false);
      case DEADLINE_EXCEEDED ->
          new Mapped(
              HttpStatus.GATEWAY_TIMEOUT,
              "UPSTREAM_TIMEOUT",
              "The service took too long to answer",
              true);
      case UNAVAILABLE ->
          new Mapped(
              HttpStatus.SERVICE_UNAVAILABLE,
              "UPSTREAM_UNAVAILABLE",
              "The service is not available",
              true);
      default -> new Mapped(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", INTERNAL_DETAIL, true);
    };
  }

  private static Mapped business(
      HttpStatus status, String published, String fallback, DownstreamCallException e) {
    return new Mapped(status, published != null ? published : fallback, e.getMessage(), false);
  }
}
