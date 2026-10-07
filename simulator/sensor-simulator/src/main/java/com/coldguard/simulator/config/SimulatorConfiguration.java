package com.coldguard.simulator.config;

import com.coldguard.simulator.application.ReadingBuffer;
import com.coldguard.simulator.application.ReadingSender;
import com.coldguard.simulator.application.SimulatorMetrics;
import com.coldguard.simulator.application.SimulatorTick;
import com.coldguard.simulator.domain.ReadingPlanner;
import com.coldguard.simulator.domain.SensorSpec;
import com.coldguard.simulator.infrastructure.GrpcReadingSender;
import com.coldguard.simulator.infrastructure.MicrometerSimulatorMetrics;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
@ConditionalOnProperty(
    name = "coldguard.simulator.enabled",
    havingValue = "true",
    matchIfMissing = true)
class SimulatorConfiguration {

  private static final Metadata.Key<String> CORRELATION_ID =
      Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

  @Bean
  Clock simulatorClock() {
    return Clock.systemUTC();
  }

  /** Each call carries its own correlation id, so one delivery can be followed across services. */
  @Bean
  TelemetryServiceGrpc.TelemetryServiceBlockingStub telemetryStub(GrpcChannelFactory channels) {
    ClientInterceptor correlation =
        new ClientInterceptor() {
          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, options)) {
              @Override
              public void start(Listener<RespT> listener, Metadata headers) {
                headers.put(CORRELATION_ID, "sim-" + UUID.randomUUID());
                super.start(listener, headers);
              }
            };
          }
        };
    return TelemetryServiceGrpc.newBlockingStub(channels.createChannel("telemetry-service"))
        .withInterceptors(correlation);
  }

  @Bean
  ReadingSender readingSender(
      TelemetryServiceGrpc.TelemetryServiceBlockingStub stub, SimulatorProperties properties) {
    return new GrpcReadingSender(stub, properties.deadline());
  }

  @Bean
  SimulatorMetrics simulatorMetrics(MeterRegistry registry) {
    return new MicrometerSimulatorMetrics(registry);
  }

  @Bean
  SimulatorTick simulatorTick(
      SimulatorProperties properties, ReadingSender sender, SimulatorMetrics metrics, Clock clock) {
    List<SensorSpec> specs = ScenarioValidator.validate(properties.sensors());
    if (specs.isEmpty()) {
      throw new IllegalStateException(
          "The simulator is enabled but has no sensors: generate the scenario file"
              + " (deploy/scripts/generate-simulator-scenario.sh) or set coldguard.simulator.enabled=false");
    }
    ReadingPlanner planner = new ReadingPlanner(specs, properties.randomSeed(), clock.instant());
    return new SimulatorTick(
        planner,
        new ReadingBuffer(properties.buffer().maxPendingReadings()),
        sender,
        metrics,
        clock,
        properties.maxBatchSize(),
        properties.backoff().initial(),
        properties.backoff().max());
  }
}
