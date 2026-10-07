package com.coldguard.incident.infrastructure;

import com.coldguard.incident.application.IncidentQueryLimits;
import com.coldguard.incident.domain.SlaPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IncidentProperties.class)
class IncidentConfiguration {

  @Bean
  SlaPolicy slaPolicy(IncidentProperties properties) {
    return new ConfiguredSlaPolicy(properties.sla());
  }

  @Bean
  IncidentQueryLimits incidentQueryLimits(IncidentProperties properties) {
    return new IncidentQueryLimits(properties.defaultPageSize(), properties.maxPageSize());
  }
}
