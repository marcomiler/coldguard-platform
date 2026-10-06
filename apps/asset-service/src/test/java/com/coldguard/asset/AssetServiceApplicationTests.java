package com.coldguard.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
@SpringBootTest(properties = "spring.grpc.server.port=0")
class AssetServiceApplicationTests {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.hikari.schema", () -> "asset");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
  }

  @Autowired DomainEventPublisher publisher;
  @Autowired OutboxRelay relay;
  @Autowired InboxGuard inbox;
  @Autowired JdbcClient jdbc;

  @Test
  void contextLoadsWithReliableMessagingWired() {
    assertThat(publisher).isNotNull();
    assertThat(relay).isNotNull();
    assertThat(inbox).isNotNull();
  }

  @Test
  void migrationsCreateTheMessagingTablesInTheServiceSchema() {
    Integer tables =
        jdbc.sql(
                "SELECT count(*) FROM information_schema.tables"
                    + " WHERE table_schema = 'asset'"
                    + " AND table_name IN ('outbox_event', 'processed_message')")
            .query(Integer.class)
            .single();
    assertThat(tables).isEqualTo(2);
  }
}
