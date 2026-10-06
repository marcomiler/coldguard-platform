package com.coldguard.telemetry.api;

import com.coldguard.telemetry.application.AssetUnavailableException;
import com.coldguard.telemetry.application.BatchTooLargeException;
import com.coldguard.telemetry.application.RangeTooWideException;
import com.coldguard.telemetry.application.TelemetryAccessDeniedException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * Maps failures to gRPC statuses with the stable business code in the {@code x-error-code} trailer.
 * An unavailable Asset is UNAVAILABLE so the producer retries (ingestion is idempotent); nothing
 * about an internal error is described to the caller.
 */
@Component
public class TelemetryGrpcExceptionHandler implements GrpcExceptionHandler {

  static final Metadata.Key<String> ERROR_CODE =
      Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

  private static final Logger log = LoggerFactory.getLogger(TelemetryGrpcExceptionHandler.class);

  @Override
  public StatusException handleException(Throwable exception) {
    return switch (exception) {
      case TelemetryAccessDeniedException denied ->
          Status.PERMISSION_DENIED.withDescription(denied.getMessage()).asException();
      case BatchTooLargeException tooLarge ->
          coded(Status.INVALID_ARGUMENT, tooLarge.getMessage(), "BATCH_TOO_LARGE");
      case RangeTooWideException tooWide ->
          coded(Status.INVALID_ARGUMENT, tooWide.getMessage(), "RANGE_TOO_WIDE");
      case IllegalArgumentException invalid ->
          Status.INVALID_ARGUMENT.withDescription(invalid.getMessage()).asException();
      case AssetUnavailableException unavailable -> {
        log.error("The Asset service could not provide the evaluation context", unavailable);
        yield Status.UNAVAILABLE
            .withDescription("The sensor information is not available")
            .asException();
      }
      default -> {
        log.error("Unexpected error in the telemetry service", exception);
        yield Status.INTERNAL.withDescription("Unexpected error").asException();
      }
    };
  }

  private static StatusException coded(Status status, String description, String code) {
    Metadata trailers = new Metadata();
    trailers.put(ERROR_CODE, code);
    return status.withDescription(description).asException(trailers);
  }
}
