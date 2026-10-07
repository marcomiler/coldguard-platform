package com.coldguard.incident.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.ActorNotAuthorizedException;
import com.coldguard.incident.application.ConcurrentIncidentUpdateException;
import com.coldguard.incident.application.IncidentNotFoundException;
import com.coldguard.incident.domain.IncidentAlreadyAcknowledgedException;
import com.coldguard.incident.domain.IncidentAlreadyClosedException;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.junit.jupiter.api.Test;

class IncidentGrpcExceptionHandlerTest {

  private final IncidentGrpcExceptionHandler handler = new IncidentGrpcExceptionHandler();

  @Test
  void handleException_mapsIncidentAlreadyOpen_toAlreadyExistsWithMetadata() {
    StatusException result =
        handler.handleException(new IncidentAlreadyOpenException("existing-id"));

    assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
    Metadata trailers = Status.trailersFromThrowable(result);
    assertThat(trailers).isNotNull();
    assertThat(trailers.get(IncidentGrpcExceptionHandler.EXISTING_INCIDENT_ID_KEY))
        .isEqualTo("existing-id");
    assertThat(trailers.get(IncidentGrpcExceptionHandler.ERROR_CODE_KEY))
        .isEqualTo("INCIDENT_ALREADY_EXISTS");
  }

  @Test
  void handleException_mapsIllegalArgument_toInvalidArgument() {
    StatusException result =
        handler.handleException(new IllegalArgumentException("magnitud is required"));

    assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void handleException_mapsUnknownException_toInternal() {
    StatusException result = handler.handleException(new RuntimeException("boom"));

    assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.INTERNAL);
  }

  @Test
  void handleException_mapsNotFoundAndPermissionAndConflict() {
    assertThat(code(new IncidentNotFoundException("i"))).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(code(new ActorNotAuthorizedException(null, Role.AUDITOR)))
        .isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(code(new ConcurrentIncidentUpdateException("i", null)))
        .isEqualTo(Status.Code.ABORTED);
  }

  @Test
  void handleException_mapsStateConflicts_toFailedPreconditionWithErrorCode() {
    StatusException closed = handler.handleException(new IncidentAlreadyClosedException("i"));
    StatusException acknowledged =
        handler.handleException(new IncidentAlreadyAcknowledgedException("i"));

    assertThat(Status.fromThrowable(closed).getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    assertThat(closed.getTrailers().get(IncidentGrpcExceptionHandler.ERROR_CODE_KEY))
        .isEqualTo("INCIDENT_ALREADY_CLOSED");
    assertThat(acknowledged.getTrailers().get(IncidentGrpcExceptionHandler.ERROR_CODE_KEY))
        .isEqualTo("INCIDENT_ALREADY_ACKNOWLEDGED");
  }

  private Status.Code code(Throwable t) {
    return Status.fromThrowable(handler.handleException(t)).getCode();
  }

  @Test
  void handleException_publishesStableBusinessCodesForNotFoundAndInvalidRequests() {
    assertThat(
            handler
                .handleException(new IncidentNotFoundException("i"))
                .getTrailers()
                .get(IncidentGrpcExceptionHandler.ERROR_CODE_KEY))
        .isEqualTo("INCIDENT_NOT_FOUND");
    assertThat(
            handler
                .handleException(new IllegalArgumentException("bad"))
                .getTrailers()
                .get(IncidentGrpcExceptionHandler.ERROR_CODE_KEY))
        .isEqualTo("INVALID_INCIDENT_REQUEST");
  }
}
