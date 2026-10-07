package com.coldguard.incident.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.Criticality;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import com.coldguard.incident.grpc.v1.Magnitude;
import com.coldguard.incident.grpc.v1.Priority;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the service method directly through a captured {@link StreamObserver}, without a real
 * gRPC transport. IncidentRepository is an in-memory fake, not the real persistence adapter (see
 * IncidentRepositoryAdapterTest for that). closeIncident() reads the actor role from gRPC Context
 * (populated by ActorServerInterceptor in production, not run here), so tests set it explicitly via
 * Context.current().withValue(...).
 */
class IncidentGrpcServiceTest {

  private static final Actor AUTHORIZED_ROLE =
      new Actor("tech-1", Set.of(Role.MAINTENANCE_TECHNICIAN));

  private InMemoryIncidentRepository repository;
  private IncidentGrpcService grpcService;

  @BeforeEach
  void setUp() {
    repository = new InMemoryIncidentRepository();
    GrpcServices services = GrpcServices.over(repository);
    // Mimics Spring gRPC's exception-handler interceptor, which maps thrown exceptions to a
    // status in the running server.
    IncidentGrpcExceptionHandler exceptionHandler = new IncidentGrpcExceptionHandler();
    grpcService =
        new IncidentGrpcService(
            services.create,
            services.acknowledge,
            services.escalate,
            services.close,
            services.query) {
          @Override
          public void createIncident(
              CreateIncidentRequest request,
              StreamObserver<CreateIncidentResponse> responseObserver) {
            try {
              super.createIncident(request, responseObserver);
            } catch (RuntimeException ex) {
              responseObserver.onError(exceptionHandler.handleException(ex));
            }
          }

          @Override
          public void closeIncident(
              CloseIncidentRequest request,
              StreamObserver<CloseIncidentResponse> responseObserver) {
            try {
              super.closeIncident(request, responseObserver);
            } catch (RuntimeException ex) {
              responseObserver.onError(exceptionHandler.handleException(ex));
            }
          }
        };
  }

  @Test
  void createIncident_validRequest_returnsCalculatedIncident() {
    CreateIncidentRequest request =
        CreateIncidentRequest.newBuilder()
            .setAssetId("asset-1")
            .setAssetCriticality(Criticality.CRITICALITY_CRITICAL)
            .setSensorId("sensor-1")
            .setAnomalyType("high-temperature")
            .setMagnitude(Magnitude.MAGNITUDE_CRITICAL)
            .setPersistent(true)
            .setCorrelationId("corr-1")
            .build();
    CapturingObserver<CreateIncidentResponse> observer = new CapturingObserver<>();

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
    CreateIncidentRequest request =
        CreateIncidentRequest.newBuilder()
            .setAssetId("asset-1")
            .setAssetCriticality(Criticality.CRITICALITY_MEDIUM)
            .setSensorId("sensor-1")
            .setAnomalyType("high-temperature")
            .setMagnitude(Magnitude.MAGNITUDE_MEDIUM)
            .setPersistent(false)
            .setCorrelationId("corr-1")
            .build();
    grpcService.createIncident(request, new CapturingObserver<>());
    CapturingObserver<CreateIncidentResponse> secondObserver = new CapturingObserver<>();

    grpcService.createIncident(request, secondObserver);

    assertThat(secondObserver.response).isNull();
    assertThat(secondObserver.error).isNotNull();
    assertThat(Status.fromThrowable(secondObserver.error).getCode())
        .isEqualTo(Status.Code.ALREADY_EXISTS);
  }

  @Test
  void toGrpcStatus_mapsClosed() {
    assertThat(IncidentGrpcMapper.status(com.coldguard.incident.domain.IncidentStatus.CLOSED))
        .isEqualTo(IncidentStatus.CLOSED);
  }

  @Test
  void createIncident_missingCriticality_returnsInvalidArgument() {
    CreateIncidentRequest request =
        CreateIncidentRequest.newBuilder()
            .setAssetId("asset-1")
            .setSensorId("sensor-1")
            .setAnomalyType("high-temperature")
            .setMagnitude(Magnitude.MAGNITUDE_MEDIUM)
            .build();
    CapturingObserver<CreateIncidentResponse> observer = new CapturingObserver<>();

    grpcService.createIncident(request, observer);

    assertThat(observer.response).isNull();
    assertThat(observer.error).isNotNull();
    assertThat(Status.fromThrowable(observer.error).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void closeIncident_authorizedActor_returnsClosedIncident() throws Exception {
    Incident incident = newStoredIncident("incident-1");
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId(incident.id())
            .setCause("overheating")
            .setResolutionComment("replaced sensor")
            .build();
    CapturingObserver<CloseIncidentResponse> observer = new CapturingObserver<>();

    withActorRole(AUTHORIZED_ROLE, () -> grpcService.closeIncident(request, observer));

    assertThat(observer.error).isNull();
    assertThat(observer.response).isNotNull();
    assertThat(observer.response.getStatus()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(observer.completed).isTrue();
  }

  @Test
  void closeIncident_unauthorizedActor_returnsPermissionDenied() throws Exception {
    Incident incident = newStoredIncident("incident-2");
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId(incident.id())
            .setCause("overheating")
            .setResolutionComment("replaced sensor")
            .build();
    CapturingObserver<CloseIncidentResponse> observer = new CapturingObserver<>();

    withActorRole(
        new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR)),
        () -> grpcService.closeIncident(request, observer));

    assertThat(observer.response).isNull();
    assertThat(Status.fromThrowable(observer.error).getCode())
        .isEqualTo(Status.Code.PERMISSION_DENIED);
  }

  @Test
  void closeIncident_unknownIncident_returnsNotFound() throws Exception {
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId("missing")
            .setCause("overheating")
            .setResolutionComment("replaced sensor")
            .build();
    CapturingObserver<CloseIncidentResponse> observer = new CapturingObserver<>();

    withActorRole(AUTHORIZED_ROLE, () -> grpcService.closeIncident(request, observer));

    assertThat(observer.response).isNull();
    assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.NOT_FOUND);
  }

  private Incident newStoredIncident(String id) {
    Incident incident =
        com.coldguard.incident.domain.Incident.open(
                java.util.UUID.nameUUIDFromBytes(id.getBytes()).toString(),
                "asset-1",
                "sensor-1",
                "high-temperature",
                com.coldguard.incident.domain.Criticality.HIGH,
                com.coldguard.incident.domain.Magnitude.HIGH,
                false,
                null,
                com.coldguard.incident.domain.DomainFixtures.SLA,
                java.time.Clock.systemUTC())
            .incident();
    repository.save(incident);
    return incident;
  }

  private static void withActorRole(Actor actor, Runnable runnable) throws Exception {
    Context context = Context.current().withValue(ActorServerInterceptor.ACTOR_CONTEXT_KEY, actor);
    Context previous = context.attach();
    try {
      runnable.run();
    } finally {
      context.detach(previous);
    }
  }

  private static final class InMemoryIncidentRepository implements IncidentRepository {
    private final ConcurrentHashMap<String, Incident> incidentsById = new ConcurrentHashMap<>();

    @Override
    public void save(Incident incident) {
      incidentsById.put(incident.id(), incident);
    }

    @Override
    public Optional<Incident> findOpen(String assetId, String sensorId, String anomalyType) {
      return incidentsById.values().stream()
          .filter(i -> i.status().isOpen())
          .filter(
              i ->
                  i.assetId().equals(assetId)
                      && i.sensorId().equals(sensorId)
                      && i.anomalyType().equals(anomalyType))
          .findFirst();
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
      return Optional.ofNullable(incidentsById.get(incidentId));
    }

    @Override
    public void update(Incident incident) {
      incidentsById.put(incident.id(), incident);
    }

    @Override
    public com.coldguard.incident.application.PageResult<Incident> search(
        com.coldguard.incident.application.IncidentSearch search,
        com.coldguard.incident.application.PageQuery page) {
      java.util.List<Incident> all = new java.util.ArrayList<>(incidentsById.values());
      return new com.coldguard.incident.application.PageResult<>(
          all, page.page(), page.size(), all.size());
    }
  }

  private static final class CapturingObserver<T> implements StreamObserver<T> {
    private T response;
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(T value) {
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
