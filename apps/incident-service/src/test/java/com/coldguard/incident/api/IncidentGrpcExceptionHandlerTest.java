package com.coldguard.incident.api;

import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentGrpcExceptionHandlerTest {

    private final IncidentGrpcExceptionHandler handler = new IncidentGrpcExceptionHandler();

    @Test
    void handleException_mapsIncidentAlreadyOpen_toAlreadyExistsWithMetadata() {
        StatusException result = handler.handleException(new IncidentAlreadyOpenException("existing-id"));

        assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        Metadata trailers = Status.trailersFromThrowable(result);
        assertThat(trailers).isNotNull();
        assertThat(trailers.get(IncidentGrpcExceptionHandler.EXISTING_INCIDENT_ID_KEY)).isEqualTo("existing-id");
    }

    @Test
    void handleException_mapsIllegalArgument_toInvalidArgument() {
        StatusException result = handler.handleException(new IllegalArgumentException("magnitud is required"));

        assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void handleException_mapsUnknownException_toInternal() {
        StatusException result = handler.handleException(new RuntimeException("boom"));

        assertThat(Status.fromThrowable(result).getCode()).isEqualTo(Status.Code.INTERNAL);
    }
}
