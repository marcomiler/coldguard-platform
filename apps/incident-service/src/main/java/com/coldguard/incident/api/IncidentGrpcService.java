package com.coldguard.incident.api;

import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.incident.application.AcknowledgeIncidentCommand;
import com.coldguard.incident.application.AcknowledgeIncidentService;
import com.coldguard.incident.application.CloseIncidentCommand;
import com.coldguard.incident.application.CloseIncidentService;
import com.coldguard.incident.application.CreateIncidentCommand;
import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.application.EscalateIncidentCommand;
import com.coldguard.incident.application.EscalateIncidentService;
import com.coldguard.incident.application.IncidentQueryService;
import com.coldguard.incident.grpc.v1.AcknowledgeIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.EscalateIncidentRequest;
import com.coldguard.incident.grpc.v1.GetIncidentRequest;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentView;
import com.coldguard.incident.grpc.v1.ListIncidentsRequest;
import com.coldguard.incident.grpc.v1.ListIncidentsResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint of the incident lifecycle (contracts/grpc/incident/v1/incident_service.proto). It
 * only maps messages; exceptions are mapped to statuses by {@link IncidentGrpcExceptionHandler} and
 * the correlation id is placed in the MDC by the shared server interceptor.
 */
@GrpcService
public class IncidentGrpcService extends IncidentServiceGrpc.IncidentServiceImplBase {

  private final CreateIncidentService createIncidentService;
  private final AcknowledgeIncidentService acknowledgeIncidentService;
  private final EscalateIncidentService escalateIncidentService;
  private final CloseIncidentService closeIncidentService;
  private final IncidentQueryService queryService;

  public IncidentGrpcService(
      CreateIncidentService createIncidentService,
      AcknowledgeIncidentService acknowledgeIncidentService,
      EscalateIncidentService escalateIncidentService,
      CloseIncidentService closeIncidentService,
      IncidentQueryService queryService) {
    this.createIncidentService = createIncidentService;
    this.acknowledgeIncidentService = acknowledgeIncidentService;
    this.escalateIncidentService = escalateIncidentService;
    this.closeIncidentService = closeIncidentService;
    this.queryService = queryService;
  }

  @Override
  public void createIncident(
      CreateIncidentRequest request, StreamObserver<CreateIncidentResponse> responseObserver) {
    var incident =
        createIncidentService.create(
            new CreateIncidentCommand(
                IncidentGrpcMapper.toOpenCommand(request),
                ActorServerInterceptor.ACTOR_CONTEXT_KEY.get()));
    responseObserver.onNext(IncidentGrpcMapper.toCreateResponse(incident));
    responseObserver.onCompleted();
  }

  @Override
  public void acknowledgeIncident(
      AcknowledgeIncidentRequest request, StreamObserver<IncidentView> responseObserver) {
    var incident =
        acknowledgeIncidentService.acknowledge(
            new AcknowledgeIncidentCommand(
                request.getIncidentId(), ActorServerInterceptor.ACTOR_CONTEXT_KEY.get()));
    responseObserver.onNext(IncidentGrpcMapper.toView(incident));
    responseObserver.onCompleted();
  }

  @Override
  public void escalateIncident(
      EscalateIncidentRequest request, StreamObserver<IncidentView> responseObserver) {
    var incident =
        escalateIncidentService.escalate(
            new EscalateIncidentCommand(
                request.getIncidentId(),
                request.getReason(),
                ActorServerInterceptor.ACTOR_CONTEXT_KEY.get()));
    responseObserver.onNext(IncidentGrpcMapper.toView(incident));
    responseObserver.onCompleted();
  }

  @Override
  public void closeIncident(
      CloseIncidentRequest request, StreamObserver<CloseIncidentResponse> responseObserver) {
    var incident =
        closeIncidentService.close(
            new CloseIncidentCommand(
                request.getIncidentId(),
                request.getCause(),
                request.getResolutionComment(),
                ActorServerInterceptor.ACTOR_CONTEXT_KEY.get()));
    responseObserver.onNext(IncidentGrpcMapper.toCloseResponse(incident));
    responseObserver.onCompleted();
  }

  @Override
  public void getIncident(
      GetIncidentRequest request, StreamObserver<IncidentView> responseObserver) {
    responseObserver.onNext(IncidentGrpcMapper.toView(queryService.get(request.getIncidentId())));
    responseObserver.onCompleted();
  }

  @Override
  public void listIncidents(
      ListIncidentsRequest request, StreamObserver<ListIncidentsResponse> responseObserver) {
    var result =
        queryService.list(
            IncidentGrpcMapper.toSearch(request),
            IncidentGrpcMapper.toPageQuery(request.getPage()));
    responseObserver.onNext(IncidentGrpcMapper.toListResponse(result));
    responseObserver.onCompleted();
  }
}
