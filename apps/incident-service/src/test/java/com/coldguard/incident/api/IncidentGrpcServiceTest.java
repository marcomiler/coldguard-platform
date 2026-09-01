package com.coldguard.incident.api;

import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.Criticality;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import com.coldguard.incident.grpc.v1.Magnitude;
import com.coldguard.incident.grpc.v1.Priority;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the service method directly through a captured {@link StreamObserver}, without a
 * real gRPC transport. IncidentRepository is an in-memory fake, not the real persistence
 * adapter (see IncidentRepositoryAdapterTest for that).
 */
class IncidentGrpcServiceTest {

    private IncidentGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new IncidentGrpcService(
                new CreateIncidentService(new InMemoryIncidentRepository()), new IncidentGrpcExceptionHandler());
    }

    @Test
    void createIncident_validRequest_returnsCalculatedIncident() {
        CreateIncidentRequest request = CreateIncidentRequest.newBuilder()
                .setAssetId("asset-1")
                .setAssetCriticality(Criticality.CRITICALITY_CRITICAL)
                .setSensorId("sensor-1")
                .setAnomalyType("high-temperature")
                .setMagnitude(Magnitude.MAGNITUDE_CRITICAL)
                .setPersistent(true)
                .setCorrelationId("corr-1")
                .build();
        CapturingObserver observer = new CapturingObserver();

        grpcService.createIncident(request, observer);

        assertThat(observer.error).isNull();
        assertThat(observer.response).isNotNull();
        assertThat(observer.response.getIncidentId()).isNotBlank();
        assertThat(observer.response.getPriority()).isEqualTo(Priority.P1);
        assertThat(observer.response.getStatus()).isEqualTo(IncidentStatus.CREATED);
        assertThat(observer.completed).isTrue();
    }

    @Test
    void createIncident_duplicateRequest_returnsAlreadyExists() {
        CreateIncidentRequest request = CreateIncidentRequest.newBuilder()
                .setAssetId("asset-1")
                .setAssetCriticality(Criticality.CRITICALITY_MEDIUM)
                .setSensorId("sensor-1")
                .setAnomalyType("high-temperature")
                .setMagnitude(Magnitude.MAGNITUDE_MEDIUM)
                .setPersistent(false)
                .setCorrelationId("corr-1")
                .build();
        grpcService.createIncident(request, new CapturingObserver());
        CapturingObserver secondObserver = new CapturingObserver();

        grpcService.createIncident(request, secondObserver);

        assertThat(secondObserver.response).isNull();
        assertThat(secondObserver.error).isNotNull();
        assertThat(Status.fromThrowable(secondObserver.error).getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void toGrpcStatus_mapsClosed() {
        assertThat(IncidentGrpcService.toGrpcStatus(com.coldguard.incident.domain.IncidentStatus.CLOSED))
                .isEqualTo(IncidentStatus.CLOSED);
    }

    @Test
    void createIncident_missingCriticality_returnsInvalidArgument() {
        CreateIncidentRequest request = CreateIncidentRequest.newBuilder()
                .setAssetId("asset-1")
                .setSensorId("sensor-1")
                .setAnomalyType("high-temperature")
                .setMagnitude(Magnitude.MAGNITUDE_MEDIUM)
                .build();
        CapturingObserver observer = new CapturingObserver();

        grpcService.createIncident(request, observer);

        assertThat(observer.response).isNull();
        assertThat(observer.error).isNotNull();
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private static final class InMemoryIncidentRepository implements IncidentRepository {
        private final ConcurrentHashMap<String, String> openIncidentIdByKey = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Incident> incidentsById = new ConcurrentHashMap<>();

        @Override
        public void save(Incident incident) {
            String key = incident.assetId() + "|" + incident.sensorId() + "|" + incident.anomalyType();
            openIncidentIdByKey.putIfAbsent(key, incident.id());
            incidentsById.put(incident.id(), incident);
        }

        @Override
        public Optional<String> findOpenIncidentId(String assetId, String sensorId, String anomalyType) {
            return Optional.ofNullable(openIncidentIdByKey.get(assetId + "|" + sensorId + "|" + anomalyType));
        }

        @Override
        public Optional<Incident> findById(String incidentId) {
            return Optional.ofNullable(incidentsById.get(incidentId));
        }

        @Override
        public void update(Incident incident) {
            incidentsById.put(incident.id(), incident);
        }
    }

    private static final class CapturingObserver implements StreamObserver<CreateIncidentResponse> {
        private CreateIncidentResponse response;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(CreateIncidentResponse value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
