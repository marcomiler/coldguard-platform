package com.coldguard.incident.api;

import com.coldguard.incident.application.ActorNotAuthorizedException;
import com.coldguard.incident.application.ConcurrentIncidentUpdateException;
import com.coldguard.incident.application.IncidentNotFoundException;
import com.coldguard.incident.domain.IncidentAlreadyAcknowledgedException;
import com.coldguard.incident.domain.IncidentAlreadyClosedException;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/** The single place where application and domain exceptions become gRPC statuses. */
@Component
public class IncidentGrpcExceptionHandler implements GrpcExceptionHandler {

  static final Metadata.Key<String> EXISTING_INCIDENT_ID_KEY =
      Metadata.Key.of("existing-incident-id", Metadata.ASCII_STRING_MARSHALLER);

  static final Metadata.Key<String> ERROR_CODE_KEY =
      Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public StatusException handleException(Throwable exception) {
    return switch (exception) {
      case IncidentAlreadyOpenException ex -> {
        Metadata trailers = new Metadata();
        trailers.put(EXISTING_INCIDENT_ID_KEY, ex.getExistingIncidentId());
        trailers.put(ERROR_CODE_KEY, "INCIDENT_ALREADY_EXISTS");
        yield Status.ALREADY_EXISTS
            .withDescription("An open incident already exists for this asset/sensor/anomaly type")
            .asException(trailers);
      }
      case IllegalArgumentException ex -> {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, "INVALID_INCIDENT_REQUEST");
        yield Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asException(trailers);
      }
      case IncidentNotFoundException ex -> {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, "INCIDENT_NOT_FOUND");
        yield Status.NOT_FOUND.withDescription(ex.getMessage()).asException(trailers);
      }
      case IncidentAlreadyClosedException ex ->
          failedPrecondition(ex.getMessage(), "INCIDENT_ALREADY_CLOSED");
      case IncidentAlreadyAcknowledgedException ex ->
          failedPrecondition(ex.getMessage(), "INCIDENT_ALREADY_ACKNOWLEDGED");
      case ActorNotAuthorizedException ex ->
          Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asException();
      case ConcurrentIncidentUpdateException ex ->
          Status.ABORTED.withDescription(ex.getMessage()).asException();
      default ->
          Status.INTERNAL.withDescription("Unexpected error").withCause(exception).asException();
    };
  }

  private static StatusException failedPrecondition(String message, String code) {
    Metadata trailers = new Metadata();
    trailers.put(ERROR_CODE_KEY, code);
    return Status.FAILED_PRECONDITION.withDescription(message).asException(trailers);
  }
}
