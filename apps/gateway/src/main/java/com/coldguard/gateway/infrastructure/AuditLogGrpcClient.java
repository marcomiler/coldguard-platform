package com.coldguard.gateway.infrastructure;

import com.coldguard.audit.grpc.v1.AuditLogServiceGrpc;
import com.coldguard.audit.grpc.v1.ListAuditRecordsRequest;
import com.coldguard.audit.grpc.v1.ListAuditRecordsResponse;
import com.coldguard.gateway.config.DownstreamProperties;
import org.springframework.stereotype.Component;

@Component
public class AuditLogGrpcClient {

  private static final String SERVICE = "incident-service";

  private final AuditLogServiceGrpc.AuditLogServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public AuditLogGrpcClient(
      AuditLogServiceGrpc.AuditLogServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  public ListAuditRecordsResponse listAuditRecords(ListAuditRecordsRequest request) {
    return invoker.query(
        SERVICE, stub, properties.deadline(SERVICE), s -> s.listAuditRecords(request));
  }
}
