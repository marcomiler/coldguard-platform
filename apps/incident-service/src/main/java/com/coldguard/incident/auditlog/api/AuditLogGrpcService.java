package com.coldguard.incident.auditlog.api;

import com.coldguard.audit.grpc.v1.AuditLogServiceGrpc;
import com.coldguard.audit.grpc.v1.AuditRecordView;
import com.coldguard.audit.grpc.v1.ListAuditRecordsRequest;
import com.coldguard.audit.grpc.v1.ListAuditRecordsResponse;
import com.coldguard.common.grpc.v1.CursorPageInfo;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.incident.auditlog.application.AuditAccessDeniedException;
import com.coldguard.incident.auditlog.application.AuditRecord;
import com.coldguard.incident.auditlog.application.ListAuditRecordsService;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only gRPC endpoint of the audit log (contracts/grpc/audit/v1). */
@GrpcService
public class AuditLogGrpcService extends AuditLogServiceGrpc.AuditLogServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(AuditLogGrpcService.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final ListAuditRecordsService service;

  public AuditLogGrpcService(ListAuditRecordsService service) {
    this.service = service;
  }

  @Override
  public void listAuditRecords(
      ListAuditRecordsRequest request, StreamObserver<ListAuditRecordsResponse> observer) {
    try {
      var page =
          service.list(
              ActorServerInterceptor.ACTOR_CONTEXT_KEY.get(),
              new ListAuditRecordsService.Query(
                  request.getEntityType(),
                  request.getEntityId(),
                  request.getActorId(),
                  request.getAction(),
                  request.hasFrom() ? instant(request.getFrom()) : null,
                  request.hasTo() ? instant(request.getTo()) : null,
                  request.getPage().getCursor(),
                  request.getPage().getSize()));
      ListAuditRecordsResponse.Builder response = ListAuditRecordsResponse.newBuilder();
      page.records().forEach(record -> response.addRecords(toView(record)));
      response.setPage(
          CursorPageInfo.newBuilder().setNextCursor(page.nextCursor()).setHasMore(page.hasMore()));
      observer.onNext(response.build());
      observer.onCompleted();
    } catch (AuditAccessDeniedException e) {
      observer.onError(
          Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (RuntimeException e) {
      log.error("Unexpected error listing audit records", e);
      observer.onError(Status.INTERNAL.withDescription("Unexpected error").asRuntimeException());
    }
  }

  private static AuditRecordView toView(AuditRecord r) {
    AuditRecordView.Builder view =
        AuditRecordView.newBuilder()
            .setId(r.id().toString())
            .setOccurredAt(timestamp(r.occurredAt()))
            .setSourceService(r.sourceService())
            .setEntityType(r.entityType())
            .setEntityId(r.entityId())
            .setAction(r.action())
            .setActorType(r.actorType())
            .setActorId(r.actorId());
    if (r.reason() != null) {
      view.setReason(r.reason());
    }
    if (r.previousValue() != null) {
      view.setPreviousValue(struct(r.previousValue()));
    }
    if (r.newValue() != null) {
      view.setNewValue(struct(r.newValue()));
    }
    if (r.correlationId() != null) {
      view.setCorrelationId(r.correlationId());
    }
    return view.build();
  }

  private static Struct struct(String json) {
    Struct.Builder struct = Struct.newBuilder();
    JsonNode node = JSON.readTree(json);
    node.properties().forEach(entry -> struct.putFields(entry.getKey(), value(entry.getValue())));
    return struct.build();
  }

  private static Value value(JsonNode node) {
    if (node.isNull()) {
      return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }
    if (node.isBoolean()) {
      return Value.newBuilder().setBoolValue(node.asBoolean()).build();
    }
    if (node.isNumber()) {
      return Value.newBuilder().setNumberValue(node.asDouble()).build();
    }
    if (node.isObject()) {
      Struct.Builder nested = Struct.newBuilder();
      node.properties().forEach(e -> nested.putFields(e.getKey(), value(e.getValue())));
      return Value.newBuilder().setStructValue(nested).build();
    }
    if (node.isArray()) {
      ListValue.Builder list = ListValue.newBuilder();
      node.forEach(element -> list.addValues(value(element)));
      return Value.newBuilder().setListValue(list).build();
    }
    return Value.newBuilder().setStringValue(node.asString()).build();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
