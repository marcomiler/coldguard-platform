package com.coldguard.gateway.api.audit;

import com.coldguard.audit.grpc.v1.AuditRecordView;
import com.coldguard.audit.grpc.v1.ListAuditRecordsRequest;
import com.coldguard.common.grpc.v1.CursorPageRequest;
import com.coldguard.gateway.api.common.CursorPageResponse;
import com.coldguard.gateway.infrastructure.AuditLogGrpcClient;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit log for auditors. */
@RestController
@RequestMapping("/api/v1/audit-records")
public class AuditController {

  private final AuditLogGrpcClient audit;

  AuditController(AuditLogGrpcClient audit) {
    this.audit = audit;
  }

  @GetMapping
  CursorPageResponse<AuditRecordResponse> list(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String entityId,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String action,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    ListAuditRecordsRequest.Builder request =
        ListAuditRecordsRequest.newBuilder()
            .setFrom(timestamp(from))
            .setTo(timestamp(to))
            .setPage(
                CursorPageRequest.newBuilder()
                    .setCursor(cursor == null ? "" : cursor)
                    .setSize(size));
    if (entityType != null) {
      request.setEntityType(entityType);
    }
    if (entityId != null) {
      request.setEntityId(entityId);
    }
    if (actorId != null) {
      request.setActorId(actorId);
    }
    if (action != null) {
      request.setAction(action);
    }
    var reply = audit.listAuditRecords(request.build());
    return new CursorPageResponse<>(
        reply.getRecordsList().stream().map(AuditController::toRest).toList(),
        reply.getPage().getNextCursor(),
        reply.getPage().getHasMore());
  }

  private static AuditRecordResponse toRest(AuditRecordView r) {
    return new AuditRecordResponse(
        r.getId(),
        Instant.ofEpochSecond(r.getOccurredAt().getSeconds(), r.getOccurredAt().getNanos()),
        r.getSourceService(),
        r.getEntityType(),
        r.getEntityId(),
        r.getAction(),
        r.getActorType(),
        r.getActorId(),
        r.getReason().isEmpty() ? null : r.getReason(),
        r.hasPreviousValue() ? map(r.getPreviousValue()) : null,
        r.hasNewValue() ? map(r.getNewValue()) : null,
        r.getCorrelationId().isEmpty() ? null : r.getCorrelationId());
  }

  private static Map<String, Object> map(Struct struct) {
    Map<String, Object> out = new LinkedHashMap<>();
    struct.getFieldsMap().forEach((k, v) -> out.put(k, plain(v)));
    return out;
  }

  private static Object plain(Value value) {
    return switch (value.getKindCase()) {
      case STRING_VALUE -> value.getStringValue();
      case NUMBER_VALUE -> value.getNumberValue();
      case BOOL_VALUE -> value.getBoolValue();
      case STRUCT_VALUE -> map(value.getStructValue());
      case LIST_VALUE -> list(value.getListValue());
      case NULL_VALUE, KIND_NOT_SET -> null;
    };
  }

  private static List<Object> list(ListValue list) {
    List<Object> out = new ArrayList<>();
    list.getValuesList().forEach(v -> out.add(plain(v)));
    return out;
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
