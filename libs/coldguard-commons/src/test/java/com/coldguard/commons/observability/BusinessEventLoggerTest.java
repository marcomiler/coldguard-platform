package com.coldguard.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BusinessEventLoggerTest {

  private final BusinessEventLogger businessEvents = new BusinessEventLogger();
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger logger;

  @BeforeEach
  void attach() {
    logger = (Logger) LoggerFactory.getLogger(BusinessEventLogger.LOGGER_NAME);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(appender);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static Map<String, Object> fields(ILoggingEvent event) {
    Map<String, Object> fields = new java.util.LinkedHashMap<>();
    event.getKeyValuePairs().forEach(kv -> fields.put(kv.key, kv.value));
    return fields;
  }

  @Test
  void outsideATransactionTheEventIsWrittenAtOnceWithItsNameAndAttributes() {
    businessEvents.log("IncidentCreated", Map.of("incidentId", "i-1", "priority", "P1"));

    assertThat(appender.list).hasSize(1);
    assertThat(fields(appender.list.get(0)))
        .containsEntry("event.name", "IncidentCreated")
        .containsEntry("incidentId", "i-1")
        .containsEntry("priority", "P1");
    assertThat(appender.list.get(0).getLoggerName()).isEqualTo("coldguard.business");
  }

  @Test
  void insideATransactionTheEventIsWrittenOnlyAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();

    businessEvents.log("IncidentClosed", Map.of("incidentId", "i-1"));
    assertThat(appender.list).isEmpty();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(appender.list).hasSize(1);
  }

  @Test
  void aRolledBackTransactionNeverWritesTheEvent() {
    TransactionSynchronizationManager.initSynchronization();

    businessEvents.log("IncidentClosed", Map.of("incidentId", "i-1"));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(appender.list).isEmpty();
  }
}
