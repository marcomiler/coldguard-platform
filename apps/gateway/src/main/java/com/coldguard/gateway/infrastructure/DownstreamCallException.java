package com.coldguard.gateway.infrastructure;

import io.grpc.Status;
import java.util.Map;

/**
 * A call to an internal service that failed: the gRPC status code, the stable business code the
 * service published in the {@code x-error-code} trailer (null when it sent none), its description
 * and any extra business detail it published (for example the id of the incident that already
 * exists). The same type serves every downstream service, so the Gateway needs no exception per
 * service and code.
 */
public class DownstreamCallException extends RuntimeException {

  private final Status.Code grpcCode;
  private final String errorCode;
  private final String service;
  private final Map<String, String> details;

  public DownstreamCallException(
      String service, Status.Code grpcCode, String errorCode, String description, Throwable cause) {
    this(service, grpcCode, errorCode, description, Map.of(), cause);
  }

  public DownstreamCallException(
      String service,
      Status.Code grpcCode,
      String errorCode,
      String description,
      Map<String, String> details,
      Throwable cause) {
    super(description == null ? grpcCode.name() : description, cause);
    this.service = service;
    this.grpcCode = grpcCode;
    this.errorCode = errorCode;
    this.details = Map.copyOf(details);
  }

  public Status.Code grpcCode() {
    return grpcCode;
  }

  /** Stable business code of the contract, or null. */
  public String errorCode() {
    return errorCode;
  }

  public String service() {
    return service;
  }

  /** Extra business detail published as trailers, by REST property name; may be empty. */
  public Map<String, String> details() {
    return details;
  }
}
