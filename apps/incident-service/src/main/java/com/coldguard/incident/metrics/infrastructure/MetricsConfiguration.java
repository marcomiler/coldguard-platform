package com.coldguard.incident.metrics.infrastructure;

import com.coldguard.incident.metrics.application.MetricsQueryLimits;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MetricsConfiguration {

  @Bean
  MetricsQueryLimits metricsQueryLimits(
      @Value("${coldguard.metrics.query.max-range:93d}") Duration maxRange) {
    return new MetricsQueryLimits(maxRange);
  }
}
