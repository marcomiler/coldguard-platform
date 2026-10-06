package com.coldguard.telemetry.config;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.telemetry.application.IngestMetrics;
import com.coldguard.telemetry.application.IngestReadingsService;
import com.coldguard.telemetry.application.IngestSettings;
import com.coldguard.telemetry.application.ReadingQueryService;
import com.coldguard.telemetry.application.ReadingRepository;
import com.coldguard.telemetry.application.SensorConditionRepository;
import com.coldguard.telemetry.application.SensorContexts;
import com.coldguard.telemetry.infrastructure.AssetContextClient;
import com.coldguard.telemetry.infrastructure.CachedSensorContexts;
import com.coldguard.telemetry.infrastructure.MicrometerIngestMetrics;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelemetryProperties.class)
class TelemetryConfiguration {

  @Bean
  AssetServiceGrpc.AssetServiceBlockingStub assetStub(GrpcChannelFactory channels) {
    return AssetServiceGrpc.newBlockingStub(channels.createChannel("asset-service"));
  }

  @Bean
  AssetContextClient assetContextClient(
      AssetServiceGrpc.AssetServiceBlockingStub stub, TelemetryProperties properties) {
    return new AssetContextClient(stub, properties.assetCallDeadline());
  }

  @Bean
  CachedSensorContexts sensorContexts(AssetContextClient client, TelemetryProperties properties) {
    var cache = properties.evaluationContextCache();
    return new CachedSensorContexts(client, cache.ttl(), cache.maxSize(), Ticker.systemTicker());
  }

  @Bean
  IngestMetrics ingestMetrics(MeterRegistry meters) {
    return new MicrometerIngestMetrics(meters);
  }

  @Bean
  IngestReadingsService ingestReadingsService(
      SensorContexts contexts,
      ReadingRepository readings,
      SensorConditionRepository conditions,
      DomainEventPublisher events,
      IngestMetrics metrics,
      Clock clock,
      PlatformTransactionManager transactionManager,
      TelemetryProperties properties) {
    return new IngestReadingsService(
        contexts,
        readings,
        conditions,
        events,
        metrics,
        new IngestSettings(
            properties.ingest().maxBatchSize(), properties.ingest().futureTolerance()),
        clock,
        transactionManager);
  }

  @Bean
  ReadingQueryService readingQueryService(
      ReadingRepository readings, TelemetryProperties properties) {
    return new ReadingQueryService(
        readings,
        properties.readingsQuery().maxRange(),
        properties.page().maxSize(),
        properties.page().defaultSize());
  }
}
