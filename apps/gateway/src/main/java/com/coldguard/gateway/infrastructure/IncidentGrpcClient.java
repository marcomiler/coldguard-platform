package com.coldguard.gateway.infrastructure;

import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

/**
 * Thin adapter over the Incident Service gRPC stub: forwards the request as-is (including
 * correlation_id, already a plain field on the request) and translates a failed call into a
 * typed exception.
 */
@Component
public class IncidentGrpcClient {

    private final IncidentServiceGrpc.IncidentServiceBlockingStub stub;

    public IncidentGrpcClient(IncidentServiceGrpc.IncidentServiceBlockingStub stub) {
        this.stub = stub;
    }

    public CreateIncidentResponse createIncident(CreateIncidentRequest request) {
        try {
            return stub.createIncident(request);
        } catch (StatusRuntimeException ex) {
            throw translate(ex);
        }
    }

    public CloseIncidentResponse closeIncident(CloseIncidentRequest request) {
        try {
            return stub.closeIncident(request);
        } catch (StatusRuntimeException ex) {
            throw translate(ex);
        }
    }

    private static IncidentServiceException translate(StatusRuntimeException ex) {
        Status status = ex.getStatus();
        String message = status.getDescription();
        return switch (status.getCode()) {
            case ALREADY_EXISTS -> new IncidentAlreadyExistsException(message, ex);
            case INVALID_ARGUMENT -> new InvalidIncidentRequestException(message, ex);
            case NOT_FOUND -> new IncidentNotFoundException(message, ex);
            case FAILED_PRECONDITION -> new IncidentAlreadyClosedException(message, ex);
            case PERMISSION_DENIED -> new IncidentCloseForbiddenException(message, ex);
            default -> new IncidentServiceException(message, ex);
        };
    }
}
