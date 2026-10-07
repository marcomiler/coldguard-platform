package com.coldguard.gateway.infrastructure;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single place where a downstream gRPC call gets its deadline and where a failure becomes a
 * {@link DownstreamCallException}. Commands ({@link #call}) are never retried: they are not
 * idempotent in general. Queries ({@link #query}) are retried a configurable number of times (once
 * by default) when the service is {@code UNAVAILABLE}.
 */
@Component
public class GrpcInvoker {

  /** Trailer in which the services publish their stable business code. */
  public static final Metadata.Key<String> ERROR_CODE =
      Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

  /** Trailer with the id of the incident that already exists, by REST property name. */
  static final Metadata.Key<String> EXISTING_INCIDENT_ID =
      Metadata.Key.of("existing-incident-id", Metadata.ASCII_STRING_MARSHALLER);

  private final int queryRetries;

  public GrpcInvoker() {
    this(1);
  }

  @Autowired
  public GrpcInvoker(@Value("${coldguard.gateway.query-retries:1}") int queryRetries) {
    this.queryRetries = Math.max(0, queryRetries);
  }

  /** A command: one attempt. */
  public <S extends AbstractStub<S>, R> R call(
      String service, S stub, Duration deadline, Function<S, R> call) {
    return attempt(service, stub, deadline, call);
  }

  /** A read-only query: retried when the service is unavailable. */
  public <S extends AbstractStub<S>, R> R query(
      String service, S stub, Duration deadline, Function<S, R> call) {
    for (int retriesLeft = queryRetries; ; retriesLeft--) {
      try {
        return attempt(service, stub, deadline, call);
      } catch (DownstreamCallException e) {
        if (e.grpcCode() != Status.Code.UNAVAILABLE || retriesLeft == 0) {
          throw e;
        }
      }
    }
  }

  private <S extends AbstractStub<S>, R> R attempt(
      String service, S stub, Duration deadline, Function<S, R> call) {
    try {
      return call.apply(stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS));
    } catch (StatusRuntimeException e) {
      Metadata trailers = e.getTrailers();
      Map<String, String> details = new HashMap<>();
      if (trailers != null && trailers.get(EXISTING_INCIDENT_ID) != null) {
        details.put("existingIncidentId", trailers.get(EXISTING_INCIDENT_ID));
      }
      throw new DownstreamCallException(
          service,
          e.getStatus().getCode(),
          trailers == null ? null : trailers.get(ERROR_CODE),
          e.getStatus().getDescription(),
          details,
          e);
    }
  }
}
