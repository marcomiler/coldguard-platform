package com.coldguard.gateway.api.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.audit.grpc.v1.AuditRecordView;
import com.coldguard.audit.grpc.v1.ListAuditRecordsRequest;
import com.coldguard.audit.grpc.v1.ListAuditRecordsResponse;
import com.coldguard.common.grpc.v1.CursorPageInfo;
import com.coldguard.common.grpc.v1.PageInfo;
import com.coldguard.gateway.api.audit.AuditController;
import com.coldguard.gateway.api.metrics.MetricsController;
import com.coldguard.gateway.infrastructure.AuditLogGrpcClient;
import com.coldguard.gateway.infrastructure.DownstreamCallException;
import com.coldguard.gateway.infrastructure.IncidentOperationsGrpcClient;
import com.coldguard.gateway.infrastructure.OperationalMetricsGrpcClient;
import com.coldguard.incident.grpc.v1.AcknowledgeIncidentRequest;
import com.coldguard.incident.grpc.v1.EscalateIncidentRequest;
import com.coldguard.incident.grpc.v1.Impact;
import com.coldguard.incident.grpc.v1.IncidentView;
import com.coldguard.incident.grpc.v1.ListIncidentsRequest;
import com.coldguard.incident.grpc.v1.ListIncidentsResponse;
import com.coldguard.incident.grpc.v1.Urgency;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsRequest;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsResponse;
import com.coldguard.metrics.grpc.v1.PriorityMetrics;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Incident, metrics and audit routes: REST vocabulary, shape validation and the gRPC request. */
@WebMvcTest(
    controllers = {
      IncidentOperationsController.class,
      MetricsController.class,
      AuditController.class
    })
@AutoConfigureMockMvc(addFilters = false)
class IncidentOperationsRestApiTest {

  private static final String ID = "3f1c2f7e-0000-4000-8000-000000000001";
  private static final Timestamp T0 = Timestamp.newBuilder().setSeconds(1_790_000_000L).build();

  @Autowired private MockMvc mockMvc;
  @MockitoBean private IncidentOperationsGrpcClient incidents;
  @MockitoBean private OperationalMetricsGrpcClient metrics;
  @MockitoBean private AuditLogGrpcClient audit;

  private static IncidentView aView() {
    return IncidentView.newBuilder()
        .setIncidentId(ID)
        .setStatus(com.coldguard.incident.grpc.v1.IncidentStatus.ESCALATED)
        .setAssetId("a1")
        .setSensorId("s1")
        .setAnomalyType("TEMPERATURE_ABOVE_MAX")
        .setImpact(Impact.IMPACT_HIGH)
        .setUrgency(Urgency.URGENCY_IMMEDIATE)
        .setPriority(com.coldguard.incident.grpc.v1.Priority.P1)
        .setCreatedAt(T0)
        .setAckDueAt(T0)
        .setEscalationCount(2)
        .setOccurrenceCount(3)
        .build();
  }

  @Test
  void get_usesRestVocabularyAndOmitsAbsentValues() throws Exception {
    given(incidents.getIncident(any())).willReturn(aView());

    mockMvc
        .perform(get("/api/v1/incidents/" + ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.status").value("ESCALATED"))
        .andExpect(jsonPath("$.impact").value("HIGH"))
        .andExpect(jsonPath("$.urgency").value("IMMEDIATE"))
        .andExpect(jsonPath("$.priority").value("P1"))
        .andExpect(jsonPath("$.escalationCount").value(2))
        .andExpect(jsonPath("$.resolveDueAt").doesNotExist())
        .andExpect(jsonPath("$.cause").doesNotExist());
  }

  @Test
  void list_translatesFiltersAndPage() throws Exception {
    given(incidents.listIncidents(any()))
        .willReturn(
            ListIncidentsResponse.newBuilder()
                .addIncidents(aView())
                .setPage(
                    PageInfo.newBuilder()
                        .setPage(1)
                        .setSize(5)
                        .setTotalElements(11)
                        .setTotalPages(3))
                .build());

    mockMvc
        .perform(
            get("/api/v1/incidents")
                .param("status", "CREATED", "ESCALATED")
                .param("priority", "P1")
                .param("assetId", "a1")
                .param("createdFrom", "2026-10-01T00:00:00Z")
                .param("page", "1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ID))
        .andExpect(jsonPath("$.page.totalElements").value(11));

    ArgumentCaptor<ListIncidentsRequest> captor =
        ArgumentCaptor.forClass(ListIncidentsRequest.class);
    verify(incidents).listIncidents(captor.capture());
    ListIncidentsRequest request = captor.getValue();
    assertThat(request.getStatusesList())
        .containsExactly(
            com.coldguard.incident.grpc.v1.IncidentStatus.CREATED,
            com.coldguard.incident.grpc.v1.IncidentStatus.ESCALATED);
    assertThat(request.getPrioritiesList())
        .containsExactly(com.coldguard.incident.grpc.v1.Priority.P1);
    assertThat(request.getAssetId()).isEqualTo("a1");
    assertThat(request.hasCreatedFrom()).isTrue();
    assertThat(request.hasCreatedTo()).isFalse();
    assertThat(request.getPage().getPage()).isEqualTo(1);
    assertThat(request.getPage().getSize()).isEqualTo(5);
  }

  @Test
  void list_unknownStatus_isBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/incidents").param("status", "NOPE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void acknowledge_withoutBody_sendsTheIncidentId() throws Exception {
    given(incidents.acknowledgeIncident(any())).willReturn(aView());

    mockMvc
        .perform(post("/api/v1/incidents/" + ID + "/acknowledgement"))
        .andExpect(status().isOk());

    ArgumentCaptor<AcknowledgeIncidentRequest> captor =
        ArgumentCaptor.forClass(AcknowledgeIncidentRequest.class);
    verify(incidents).acknowledgeIncident(captor.capture());
    assertThat(captor.getValue().getIncidentId()).isEqualTo(ID);
  }

  @Test
  void acknowledge_alreadyAcknowledged_isConflictWithTheServiceCode() throws Exception {
    given(incidents.acknowledgeIncident(any()))
        .willThrow(
            new DownstreamCallException(
                "incident-service",
                Status.Code.FAILED_PRECONDITION,
                "INCIDENT_ALREADY_ACKNOWLEDGED",
                "Incident is already acknowledged",
                null));

    mockMvc
        .perform(post("/api/v1/incidents/" + ID + "/acknowledgement"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_ACKNOWLEDGED"));
  }

  @Test
  void escalate_requiresAReason() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/incidents/" + ID + "/escalation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(
            post("/api/v1/incidents/" + ID + "/escalation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void escalate_sendsTheReason() throws Exception {
    given(incidents.escalateIncident(any())).willReturn(aView());

    mockMvc
        .perform(
            post("/api/v1/incidents/" + ID + "/escalation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"no technician\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.escalationCount").value(2));

    ArgumentCaptor<EscalateIncidentRequest> captor =
        ArgumentCaptor.forClass(EscalateIncidentRequest.class);
    verify(incidents).escalateIncident(captor.capture());
    assertThat(captor.getValue().getReason()).isEqualTo("no technician");
  }

  @Test
  void metrics_mapsMeasuredValuesAndLeavesAbsentOnesOut() throws Exception {
    given(metrics.getIncidentMetrics(any()))
        .willReturn(
            GetIncidentMetricsResponse.newBuilder()
                .putCountByStatus("CLOSED", 2)
                .putCountByPriority("P1", 2)
                .setMttaSeconds(37.5)
                .addByPriority(
                    PriorityMetrics.newBuilder()
                        .setPriority("P1")
                        .setTotal(2)
                        .setAcknowledged(2)
                        .setAcknowledgedOnTime(1)
                        .setAckComplianceRatio(0.5))
                .build());

    mockMvc
        .perform(
            get("/api/v1/metrics/incidents")
                .param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-10-02T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countByStatus.CLOSED").value(2))
        .andExpect(jsonPath("$.mttaSeconds").value(37.5))
        .andExpect(jsonPath("$.mttrSeconds").doesNotExist())
        .andExpect(jsonPath("$.byPriority[0].ackComplianceRatio").value(0.5))
        .andExpect(jsonPath("$.byPriority[0].resolveComplianceRatio").doesNotExist());
    verify(metrics).getIncidentMetrics(any(GetIncidentMetricsRequest.class));
  }

  @Test
  void metrics_requireTheRange() throws Exception {
    mockMvc.perform(get("/api/v1/metrics/incidents")).andExpect(status().isBadRequest());
  }

  @Test
  void auditRecords_convertStructsToPlainJson() throws Exception {
    given(audit.listAuditRecords(any()))
        .willReturn(
            ListAuditRecordsResponse.newBuilder()
                .addRecords(
                    AuditRecordView.newBuilder()
                        .setId(ID)
                        .setOccurredAt(T0)
                        .setSourceService("incident-service")
                        .setEntityType("Incident")
                        .setEntityId("i1")
                        .setAction("CLOSED")
                        .setActorType("USER")
                        .setActorId("tech-1")
                        .setReason("overheating")
                        .setNewValue(
                            Struct.newBuilder()
                                .putFields(
                                    "status", Value.newBuilder().setStringValue("CLOSED").build())))
                .setPage(CursorPageInfo.newBuilder().setNextCursor("abc").setHasMore(true))
                .build());

    mockMvc
        .perform(
            get("/api/v1/audit-records")
                .param("entityType", "Incident")
                .param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-10-02T00:00:00Z")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("CLOSED"))
        .andExpect(jsonPath("$.items[0].reason").value("overheating"))
        .andExpect(jsonPath("$.items[0].newValue.status").value("CLOSED"))
        .andExpect(jsonPath("$.items[0].previousValue").doesNotExist())
        .andExpect(jsonPath("$.nextCursor").value("abc"))
        .andExpect(jsonPath("$.hasMore").value(true));

    ArgumentCaptor<ListAuditRecordsRequest> captor =
        ArgumentCaptor.forClass(ListAuditRecordsRequest.class);
    verify(audit).listAuditRecords(captor.capture());
    assertThat(captor.getValue().getEntityType()).isEqualTo("Incident");
    assertThat(captor.getValue().getPage().getSize()).isEqualTo(10);
  }

  @Test
  void auditRecords_requireTheRange() throws Exception {
    mockMvc.perform(get("/api/v1/audit-records")).andExpect(status().isBadRequest());
  }
}
