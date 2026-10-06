package com.coldguard.commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.correlation.CorrelationIdFilter;
import com.coldguard.commons.grpc.CorrelationClientInterceptor;
import com.coldguard.commons.grpc.CorrelationServerInterceptor;
import com.coldguard.commons.security.ActorServerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class CommonsAutoConfigurationTest {

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class));

  @Test
  void servletApp_registersFilterAndGrpcInterceptors() {
    webRunner.run(
        context -> {
          assertThat(context).hasSingleBean(CorrelationIdFilter.class);
          assertThat(context).hasSingleBean(CorrelationServerInterceptor.class);
          assertThat(context).hasSingleBean(CorrelationClientInterceptor.class);
        });
  }

  @Test
  void nonWebApp_hasNoHttpFilter() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(CorrelationIdFilter.class));
  }

  @Test
  void httpPropertyDisabled_removesFilterOnly() {
    webRunner
        .withPropertyValues("coldguard.correlation.http.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CorrelationIdFilter.class);
              assertThat(context).hasSingleBean(CorrelationServerInterceptor.class);
            });
  }

  @Test
  void grpcPropertyDisabled_removesInterceptorsOnly() {
    webRunner
        .withPropertyValues("coldguard.correlation.grpc.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CorrelationIdFilter.class);
              assertThat(context).doesNotHaveBean(CorrelationServerInterceptor.class);
              assertThat(context).doesNotHaveBean(CorrelationClientInterceptor.class);
            });
  }

  @Test
  void userDefinedFilter_takesPrecedence() {
    CorrelationIdFilter custom = new CorrelationIdFilter();
    webRunner
        .withBean("customFilter", CorrelationIdFilter.class, () -> custom)
        .run(context -> assertThat(context.getBean(CorrelationIdFilter.class)).isSameAs(custom));
  }

  @Test
  void servletApp_registersTheActorInterceptor() {
    webRunner.run(context -> assertThat(context).hasSingleBean(ActorServerInterceptor.class));
  }

  @Test
  void actorPropertyDisabled_removesTheActorInterceptor() {
    webRunner
        .withPropertyValues("coldguard.security.actor.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ActorServerInterceptor.class));
  }
}
