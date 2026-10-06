package com.coldguard.asset.api;

import com.coldguard.asset.application.AssetAccessDeniedException;
import com.coldguard.asset.application.CalibrationValidityNotConfiguredException;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.StaleVersionException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * Maps business exceptions to gRPC statuses, adding the stable business code as the {@code
 * x-error-code} trailer that the Gateway propagates without reading the text.
 */
@Component
public class AssetGrpcExceptionHandler implements GrpcExceptionHandler {

  static final Metadata.Key<String> ERROR_CODE =
      Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

  private static final Logger log = LoggerFactory.getLogger(AssetGrpcExceptionHandler.class);

  @Override
  public StatusException handleException(Throwable exception) {
    return switch (exception) {
      case AssetAccessDeniedException denied ->
          Status.PERMISSION_DENIED.withDescription(denied.getMessage()).asException();
      case ResourceNotFoundException notFound ->
          coded(Status.NOT_FOUND, notFound.getMessage(), notFound.code());
      case AlreadyExistsException exists ->
          coded(Status.ALREADY_EXISTS, exists.getMessage(), exists.code());
      case StaleVersionException stale ->
          coded(Status.ABORTED, stale.getMessage(), "CONCURRENT_MODIFICATION");
      case CalibrationValidityNotConfiguredException unconfigured ->
          coded(
              Status.FAILED_PRECONDITION,
              unconfigured.getMessage(),
              "CALIBRATION_VALIDITY_NOT_CONFIGURED");
      case IllegalArgumentException invalid ->
          Status.INVALID_ARGUMENT.withDescription(invalid.getMessage()).asException();
      default -> {
        log.error("Unexpected error in the asset service", exception);
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
