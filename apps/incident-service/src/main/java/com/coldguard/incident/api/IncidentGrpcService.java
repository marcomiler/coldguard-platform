package com.coldguard.incident.api;

import com.coldguard.incident.application.CreateIncidentCommand;
import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;
import org.springframework.grpc.server.service.GrpcService;

import java.time.format.DateTimeFormatter;

/**
 * gRPC endpoint for incident creation (contracts/grpc/incident_service.proto).
 */
@GrpcService
public class IncidentGrpcService extends IncidentServiceGrpc.IncidentServiceImplBase {

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final CreateIncidentService createIncidentService;
    private final IncidentGrpcExceptionHandler exceptionHandler;

    public IncidentGrpcService(CreateIncidentService createIncidentService,
                                IncidentGrpcExceptionHandler exceptionHandler) {
        this.createIncidentService = createIncidentService;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void createIncident(CreateIncidentRequest request,
                                StreamObserver<CreateIncidentResponse> responseObserver) {
        String correlationId = request.getCorrelationId();
        boolean correlationIdPresent = !correlationId.isBlank();
        if (correlationIdPresent) {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }
        try {
            CreateIncidentCommand command = toCommand(request);
            Incident incident = createIncidentService.create(command);
            responseObserver.onNext(toResponse(incident));
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            responseObserver.onError(exceptionHandler.handleException(ex));
        } finally {
            if (correlationIdPresent) {
                MDC.remove(CORRELATION_ID_MDC_KEY);
            }
        }
    }

    private CreateIncidentCommand toCommand(CreateIncidentRequest request) {
        return new CreateIncidentCommand(
                request.getAssetId(),
                toDomainCriticality(request.getAssetCriticality()),
                request.getSensorId(),
                request.getAnomalyType(),
                toDomainMagnitude(request.getMagnitude()),
                request.getPersistent(),
                request.getCorrelationId());
    }

    private CreateIncidentResponse toResponse(Incident incident) {
        return CreateIncidentResponse.newBuilder()
                .setIncidentId(incident.id())
                .setStatus(toGrpcStatus(incident.status()))
                .setImpact(toGrpcImpact(incident.impact()))
                .setUrgency(toGrpcUrgency(incident.urgency()))
                .setPriority(toGrpcPriority(incident.priority()))
                .setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(incident.createdAt()))
                .build();
    }

    private static Criticality toDomainCriticality(com.coldguard.incident.grpc.v1.Criticality criticality) {
        return switch (criticality) {
            case CRITICALITY_LOW -> Criticality.LOW;
            case CRITICALITY_MEDIUM -> Criticality.MEDIUM;
            case CRITICALITY_HIGH -> Criticality.HIGH;
            case CRITICALITY_CRITICAL -> Criticality.CRITICAL;
            case CRITICALITY_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("asset_criticality is required");
        };
    }

    private static Magnitude toDomainMagnitude(com.coldguard.incident.grpc.v1.Magnitude magnitude) {
        return switch (magnitude) {
            case MAGNITUDE_LOW -> Magnitude.LOW;
            case MAGNITUDE_MEDIUM -> Magnitude.MEDIUM;
            case MAGNITUDE_HIGH -> Magnitude.HIGH;
            case MAGNITUDE_CRITICAL -> Magnitude.CRITICAL;
            case MAGNITUDE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("magnitude is required");
        };
    }

    private static com.coldguard.incident.grpc.v1.Impact toGrpcImpact(Impact impact) {
        return switch (impact) {
            case LOW -> com.coldguard.incident.grpc.v1.Impact.IMPACT_LOW;
            case MEDIUM -> com.coldguard.incident.grpc.v1.Impact.IMPACT_MEDIUM;
            case HIGH -> com.coldguard.incident.grpc.v1.Impact.IMPACT_HIGH;
            case CRITICAL -> com.coldguard.incident.grpc.v1.Impact.IMPACT_CRITICAL;
        };
    }

    private static com.coldguard.incident.grpc.v1.Urgency toGrpcUrgency(Urgency urgency) {
        return switch (urgency) {
            case LOW -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_LOW;
            case MEDIUM -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_MEDIUM;
            case HIGH -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_HIGH;
            case IMMEDIATE -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_IMMEDIATE;
        };
    }

    private static com.coldguard.incident.grpc.v1.Priority toGrpcPriority(Priority priority) {
        return switch (priority) {
            case P1 -> com.coldguard.incident.grpc.v1.Priority.P1;
            case P2 -> com.coldguard.incident.grpc.v1.Priority.P2;
            case P3 -> com.coldguard.incident.grpc.v1.Priority.P3;
            case P4 -> com.coldguard.incident.grpc.v1.Priority.P4;
        };
    }

    private static com.coldguard.incident.grpc.v1.IncidentStatus toGrpcStatus(IncidentStatus status) {
        return switch (status) {
            case CREATED -> com.coldguard.incident.grpc.v1.IncidentStatus.CREATED;
        };
    }
}
