package com.coldguard.incident.api;

import com.coldguard.incident.application.IncidentCloseForbiddenException;
import com.coldguard.incident.application.IncidentNotFoundException;
import com.coldguard.incident.domain.IncidentAlreadyClosedException;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * Maps application/domain exceptions to gRPC status codes, registered as the Spring gRPC
 * exception-handling bean and also used directly by {@link IncidentGrpcService}.
 */
@Component
public class IncidentGrpcExceptionHandler implements GrpcExceptionHandler {

    static final Metadata.Key<String> EXISTING_INCIDENT_ID_KEY =
            Metadata.Key.of("existing-incident-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public StatusException handleException(Throwable exception) {
        if (exception instanceof IncidentAlreadyOpenException ex) {
            Metadata trailers = new Metadata();
            trailers.put(EXISTING_INCIDENT_ID_KEY, ex.getExistingIncidentId());
            return Status.ALREADY_EXISTS
                    .withDescription("An open incident already exists for this asset/sensor/anomaly type")
                    .asException(trailers);
        }
        if (exception instanceof IllegalArgumentException ex) {
            return Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .asException();
        }
        if (exception instanceof IncidentNotFoundException ex) {
            return Status.NOT_FOUND
                    .withDescription(ex.getMessage())
                    .asException();
        }
        if (exception instanceof IncidentAlreadyClosedException ex) {
            return Status.FAILED_PRECONDITION
                    .withDescription(ex.getMessage())
                    .asException();
        }
        if (exception instanceof IncidentCloseForbiddenException ex) {
            return Status.PERMISSION_DENIED
                    .withDescription(ex.getMessage())
                    .asException();
        }
        return Status.INTERNAL
                .withDescription("Unexpected error")
                .withCause(exception)
                .asException();
    }
}
