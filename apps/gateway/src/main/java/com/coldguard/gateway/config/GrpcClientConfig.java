package com.coldguard.gateway.config;

import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

  @Bean
  IncidentServiceGrpc.IncidentServiceBlockingStub incidentServiceBlockingStub(
      GrpcChannelFactory channels) {
    return IncidentServiceGrpc.newBlockingStub(channels.createChannel("incident-service"));
  }
}
