package com.coldguard.commons;

import com.coldguard.commons.correlation.CorrelationIdFilter;
import com.coldguard.commons.grpc.CorrelationClientInterceptor;
import com.coldguard.commons.grpc.CorrelationServerInterceptor;
import com.coldguard.commons.security.ActorServerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;

/** Each block is opt-out via property and only active when its classes are on the classpath. */
@AutoConfiguration
public class CommonsAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnProperty(
      prefix = "coldguard.correlation.http",
      name = "enabled",
      matchIfMissing = true)
  static class HttpCorrelation {

    @Bean
    @ConditionalOnMissingBean
    CorrelationIdFilter correlationIdFilter() {
      return new CorrelationIdFilter();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.grpc.server.GlobalServerInterceptor")
  @ConditionalOnProperty(
      prefix = "coldguard.correlation.grpc",
      name = "enabled",
      matchIfMissing = true)
  static class GrpcServerCorrelation {

    @Bean
    @GlobalServerInterceptor
    @ConditionalOnMissingBean
    CorrelationServerInterceptor correlationServerInterceptor() {
      return new CorrelationServerInterceptor();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.grpc.client.GlobalClientInterceptor")
  @ConditionalOnProperty(
      prefix = "coldguard.correlation.grpc",
      name = "enabled",
      matchIfMissing = true)
  static class GrpcClientCorrelation {

    @Bean
    @GlobalClientInterceptor
    @ConditionalOnMissingBean
    CorrelationClientInterceptor correlationClientInterceptor() {
      return new CorrelationClientInterceptor();
    }
  }

  /**
   * Trusts the actor propagated by the Gateway only when the mTLS peer is the Gateway; see {@link
   * ActorServerInterceptor}.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.grpc.server.GlobalServerInterceptor")
  @ConditionalOnProperty(
      prefix = "coldguard.security.actor",
      name = "enabled",
      matchIfMissing = true)
  static class GrpcServerActor {

    @Bean
    @GlobalServerInterceptor
    @ConditionalOnMissingBean
    ActorServerInterceptor actorServerInterceptor() {
      return new ActorServerInterceptor();
    }
  }
}
