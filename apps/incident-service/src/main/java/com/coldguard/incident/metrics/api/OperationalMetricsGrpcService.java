package com.coldguard.incident.metrics.api;

import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.incident.metrics.application.GetIncidentMetricsService;
import com.coldguard.incident.metrics.application.IncidentMetrics;
import com.coldguard.incident.metrics.application.MetricsAccessDeniedException;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsRequest;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsResponse;
import com.coldguard.metrics.grpc.v1.OperationalMetricsServiceGrpc;
import com.coldguard.metrics.grpc.v1.PriorityMetrics;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

/** Read-only gRPC endpoint of the operational metrics (contracts/grpc/metrics/v1). */
@GrpcService
public class OperationalMetricsGrpcService
    extends OperationalMetricsServiceGrpc.OperationalMetricsServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(OperationalMetricsGrpcService.class);

  private final GetIncidentMetricsService service;

  public OperationalMetricsGrpcService(GetIncidentMetricsService service) {
    this.service = service;
  }

  @Override
  public void getIncidentMetrics(
      GetIncidentMetricsRequest request, StreamObserver<GetIncidentMetricsResponse> observer) {
    try {
      IncidentMetrics metrics =
          service.get(
              ActorServerInterceptor.ACTOR_CONTEXT_KEY.get(),
              request.hasFrom() ? instant(request.getFrom()) : null,
              request.hasTo() ? instant(request.getTo()) : null);
      observer.onNext(toResponse(metrics));
      observer.onCompleted();
    } catch (MetricsAccessDeniedException e) {
      observer.onError(
          Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (RuntimeException e) {
      log.error("Unexpected error computing incident metrics", e);
      observer.onError(Status.INTERNAL.withDescription("Unexpected error").asRuntimeException());
    }
  }

  static GetIncidentMetricsResponse toResponse(IncidentMetrics m) {
    GetIncidentMetricsResponse.Builder response = GetIncidentMetricsResponse.newBuilder();
    m.countByStatus().forEach((status, count) -> response.putCountByStatus(status.name(), count));
    m.countByPriority().forEach((p, count) -> response.putCountByPriority(p.name(), count));
    m.mttaSeconds().ifPresent(response::setMttaSeconds);
    m.mttrSeconds().ifPresent(response::setMttrSeconds);
    m.byPriority()
        .forEach(
            (priority, d) -> {
              PriorityMetrics.Builder detail =
                  PriorityMetrics.newBuilder()
                      .setPriority(priority.name())
                      .setTotal(d.total())
                      .setAcknowledged(d.acknowledged())
                      .setAcknowledgedOnTime(d.acknowledgedOnTime())
                      .setClosed(d.closed())
                      .setClosedOnTime(d.closedOnTime());
              d.ackComplianceRatio().ifPresent(detail::setAckComplianceRatio);
              d.resolveComplianceRatio().ifPresent(detail::setResolveComplianceRatio);
              response.addByPriority(detail);
            });
    return response.build();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
