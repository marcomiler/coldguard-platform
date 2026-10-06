package com.coldguard.gateway.infrastructure;

import io.grpc.Status;

/**
 * A call to an internal service that failed: the gRPC status code, the stable business code the
 * service published in the {@code x-error-code} trailer (null when it sent none) and its
 * description. The same type serves every downstream service, so the Gateway needs no exception per
 * service and code.
 */
public class DownstreamCallException extends RuntimeException {

  private final Status.Code grpcCode;
  private final String errorCode;
  private final String service;

  public DownstreamCallException(
      String service, Status.Code grpcCode, String errorCode, String description, Throwable cause) {
    super(description == null ? grpcCode.name() : description, cause);
    this.service = service;
    this.grpcCode = grpcCode;
    this.errorCode = errorCode;
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
}
