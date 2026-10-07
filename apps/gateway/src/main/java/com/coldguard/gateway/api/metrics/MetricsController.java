package com.coldguard.gateway.api.metrics;

import com.coldguard.gateway.infrastructure.OperationalMetricsGrpcClient;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsRequest;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operational metrics of incidents: measured values, no verdict. */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

  private final OperationalMetricsGrpcClient metrics;

  MetricsController(OperationalMetricsGrpcClient metrics) {
    this.metrics = metrics;
  }

  @GetMapping("/incidents")
  IncidentMetricsResponse incidents(@RequestParam Instant from, @RequestParam Instant to) {
    var reply =
        metrics.getIncidentMetrics(
            GetIncidentMetricsRequest.newBuilder()
                .setFrom(timestamp(from))
                .setTo(timestamp(to))
                .build());
    return new IncidentMetricsResponse(
        reply.getCountByStatusMap(),
        reply.getCountByPriorityMap(),
        reply.hasMttaSeconds() ? reply.getMttaSeconds() : null,
        reply.hasMttrSeconds() ? reply.getMttrSeconds() : null,
        reply.getByPriorityList().stream()
            .map(
                d ->
                    new IncidentMetricsResponse.PriorityMetricsResponse(
                        d.getPriority(),
                        d.getTotal(),
                        d.getAcknowledged(),
                        d.getAcknowledgedOnTime(),
                        d.getClosed(),
                        d.getClosedOnTime(),
                        d.hasAckComplianceRatio() ? d.getAckComplianceRatio() : null,
                        d.hasResolveComplianceRatio() ? d.getResolveComplianceRatio() : null))
            .toList());
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
