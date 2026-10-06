package com.coldguard.asset.config;

import com.coldguard.asset.application.CalibrationPolicy;
import com.coldguard.asset.application.PageRequestPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
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
}
