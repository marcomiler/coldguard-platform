package com.coldguard.gateway.config;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.audit.grpc.v1.AuditLogServiceGrpc;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.metrics.grpc.v1.OperationalMetricsServiceGrpc;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
public class GrpcClientConfig {

  @Bean
  IncidentServiceGrpc.IncidentServiceBlockingStub incidentServiceBlockingStub(
      GrpcChannelFactory channels) {
    return IncidentServiceGrpc.newBlockingStub(channels.createChannel("incident-service"));
  }

  @Bean
  AssetServiceGrpc.AssetServiceBlockingStub assetServiceBlockingStub(GrpcChannelFactory channels) {
    return AssetServiceGrpc.newBlockingStub(channels.createChannel("asset-service"));
  }

  @Bean
  TelemetryServiceGrpc.TelemetryServiceBlockingStub telemetryServiceBlockingStub(
      GrpcChannelFactory channels) {
    return TelemetryServiceGrpc.newBlockingStub(channels.createChannel("telemetry-service"));
  }

  /** Identity & Access is a module of Incident Service, so it shares that service's channel. */
  @Bean
  IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub(
      GrpcChannelFactory channels) {
    return IdentityServiceGrpc.newBlockingStub(channels.createChannel("incident-service"));
  }

  /** The Audit Log is a module of Incident Service, so it shares that service's channel. */
  @Bean
  AuditLogServiceGrpc.AuditLogServiceBlockingStub auditLogServiceBlockingStub(
      GrpcChannelFactory channels) {
    return AuditLogServiceGrpc.newBlockingStub(channels.createChannel("incident-service"));
  }

  /** Operational metrics are a module of Incident Service too. */
  @Bean
  OperationalMetricsServiceGrpc.OperationalMetricsServiceBlockingStub
      operationalMetricsServiceBlockingStub(GrpcChannelFactory channels) {
    return OperationalMetricsServiceGrpc.newBlockingStub(
        channels.createChannel("incident-service"));
  }
}
