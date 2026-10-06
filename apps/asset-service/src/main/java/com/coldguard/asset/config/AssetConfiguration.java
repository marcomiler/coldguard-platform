package com.coldguard.asset.config;

import com.coldguard.asset.application.CalibrationExpiryService;
import com.coldguard.asset.application.CalibrationPolicy;
import com.coldguard.asset.application.CalibrationRepository;
import com.coldguard.asset.application.EvaluationContextRepository;
import com.coldguard.asset.application.EvaluationContextService;
import com.coldguard.asset.application.PageRequestPolicy;
import com.coldguard.asset.application.SensorHistoryRepository;
import com.coldguard.asset.application.SensorRepository;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AssetProperties.class)
class AssetConfiguration {

  @Bean
  PageRequestPolicy pageRequestPolicy(AssetProperties properties) {
    return new PageRequestPolicy(properties.page().maxSize(), properties.page().defaultSize());
  }

  @Bean
  CalibrationPolicy calibrationPolicy(AssetProperties properties) {
    return new CalibrationPolicy(properties.calibration().defaultValidity());
  }

  @Bean
  CalibrationExpiryService calibrationExpiryService(
      SensorRepository sensors,
      CalibrationRepository calibrations,
      SensorHistoryRepository history,
      DomainEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager,
      AssetProperties properties) {
    return new CalibrationExpiryService(
        sensors,
        calibrations,
        history,
        events,
        clock,
        transactionManager,
        properties.calibrationExpiry().batchSize());
  }

  @Bean
  EvaluationContextService evaluationContextService(
      EvaluationContextRepository contexts, AssetProperties properties) {
    return new EvaluationContextService(contexts, properties.evaluationContext().maxBatch());
  }
}
