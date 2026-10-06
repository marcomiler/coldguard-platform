package com.coldguard.gateway.config;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
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

  /** Identity & Access is a module of Incident Service, so it shares that service's channel. */
  @Bean
  IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub(
      GrpcChannelFactory channels) {
    return IdentityServiceGrpc.newBlockingStub(channels.createChannel("incident-service"));
  }
}
