package com.coldguard.incident.api;

import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.incident.application.AcknowledgeIncidentService;
import com.coldguard.incident.application.CloseIncidentService;
import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.application.EscalateIncidentService;
import com.coldguard.incident.application.IncidentQueryLimits;
import com.coldguard.incident.application.IncidentQueryService;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.application.NotificationRecipients;
import com.coldguard.incident.domain.DomainFixtures;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Builds the real use-case services over a given repository and no-op messaging, for tests of the
 * gRPC layer. A tiny Spring context is used because the services' constructors are package-private
 * on purpose.
 */
final class GrpcServices {

  final CreateIncidentService create;
  final AcknowledgeIncidentService acknowledge;
  final EscalateIncidentService escalate;
  final CloseIncidentService close;
  final IncidentQueryService query;

  private GrpcServices(AnnotationConfigApplicationContext ctx) {
    create = ctx.getBean(CreateIncidentService.class);
    acknowledge = ctx.getBean(AcknowledgeIncidentService.class);
    escalate = ctx.getBean(EscalateIncidentService.class);
    close = ctx.getBean(CloseIncidentService.class);
    query = ctx.getBean(IncidentQueryService.class);
  }

  static GrpcServices over(IncidentRepository repository) {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.registerBean(IncidentRepository.class, () -> repository);
    ctx.register(Wiring.class);
    ctx.refresh();
    return new GrpcServices(ctx);
  }

  /**
   * Deliberately not {@code @Configuration}: that is a component stereotype, and the full-context
   * tests would pick this class up by scanning and replace the real outbox with a no-op.
   */
  @ComponentScan("com.coldguard.incident.application")
  static class Wiring {

    @Bean
    DomainEventPublisher events() {
      return event -> {};
    }

    @Bean
    com.coldguard.incident.auditlog.application.AuditRecorder audit() {
      return entry -> {};
    }

    @Bean
    NotificationRecipients recipients() {
      return role -> List.of();
    }

    @Bean
    com.coldguard.incident.domain.SlaPolicy sla() {
      return DomainFixtures.SLA;
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    IncidentQueryLimits limits() {
      return new IncidentQueryLimits(20, 100);
    }
  }
}
