package com.coldguard.gateway.infrastructure;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The single place where a downstream gRPC call gets its deadline and where a failure becomes a
 * {@link DownstreamCallException}. Commands are never retried here: they are not idempotent in
 * general.
 */
@Component
public class GrpcInvoker {

  /** Trailer in which the services publish their stable business code. */
  public static final Metadata.Key<String> ERROR_CODE =
      Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

  public <S extends AbstractStub<S>, R> R call(
      String service, S stub, Duration deadline, Function<S, R> call) {
    try {
      return call.apply(stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS));
    } catch (StatusRuntimeException e) {
      Metadata trailers = e.getTrailers();
      throw new DownstreamCallException(
          service,
          e.getStatus().getCode(),
          trailers == null ? null : trailers.get(ERROR_CODE),
          e.getStatus().getDescription(),
          e);
    }
  }
}
