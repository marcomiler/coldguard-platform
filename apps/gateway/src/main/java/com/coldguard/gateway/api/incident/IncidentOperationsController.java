package com.coldguard.gateway.api.incident;

import com.coldguard.gateway.api.common.PageResponse;
import com.coldguard.gateway.infrastructure.IncidentOperationsGrpcClient;
import com.coldguard.incident.grpc.v1.AcknowledgeIncidentRequest;
import com.coldguard.incident.grpc.v1.EscalateIncidentRequest;
import com.coldguard.incident.grpc.v1.GetIncidentRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident queries and the supervisor's workflow commands. Shape validation, mapping and the call;
 * the state rules live in Incident Service.
 */
@RestController
@RequestMapping("/api/v1/incidents")
class IncidentOperationsController {

  private final IncidentOperationsGrpcClient incidents;

  IncidentOperationsController(IncidentOperationsGrpcClient incidents) {
    this.incidents = incidents;
  }

  @GetMapping
  PageResponse<IncidentResponse> list(
      @RequestParam(name = "status", defaultValue = "") List<IncidentStatus> statuses,
      @RequestParam(name = "priority", defaultValue = "") List<Priority> priorities,
      @RequestParam(required = false) String assetId,
      @RequestParam(required = false) String sensorId,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int size) {
    var reply =
        incidents.listIncidents(
            IncidentRestMapper.list(
                statuses, priorities, assetId, sensorId, createdFrom, createdTo, page, size));
    return new PageResponse<>(
        reply.getIncidentsList().stream().map(IncidentRestMapper::toRest).toList(),
        IncidentRestMapper.toRest(reply.getPage()));
  }

  @GetMapping("/{incidentId}")
  IncidentResponse get(@PathVariable String incidentId) {
    return IncidentRestMapper.toRest(
        incidents.getIncident(GetIncidentRequest.newBuilder().setIncidentId(incidentId).build()));
  }

  @PostMapping("/{incidentId}/acknowledgement")
  IncidentResponse acknowledge(
      @PathVariable String incidentId,
      @Valid @RequestBody(required = false) AcknowledgementRequest request) {
    AcknowledgeIncidentRequest.Builder grpc =
        AcknowledgeIncidentRequest.newBuilder().setIncidentId(incidentId);
    if (request != null && request.note() != null) {
      grpc.setNote(request.note());
    }
    return IncidentRestMapper.toRest(incidents.acknowledgeIncident(grpc.build()));
  }

  @PostMapping("/{incidentId}/escalation")
  IncidentResponse escalate(
      @PathVariable String incidentId, @Valid @RequestBody EscalationRequest request) {
    return IncidentRestMapper.toRest(
        incidents.escalateIncident(
            EscalateIncidentRequest.newBuilder()
                .setIncidentId(incidentId)
                .setReason(request.reason())
                .build()));
  }
}
