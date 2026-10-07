package com.coldguard.incident.auditlog.infrastructure;

import com.coldguard.incident.auditlog.application.AuditQueryLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditLogProperties.class)
class AuditLogConfiguration {

  @Bean
  AuditQueryLimits auditQueryLimits(AuditLogProperties properties) {
    return new AuditQueryLimits(
        properties.maxRange(), properties.defaultPageSize(), properties.maxPageSize());
  }
}
