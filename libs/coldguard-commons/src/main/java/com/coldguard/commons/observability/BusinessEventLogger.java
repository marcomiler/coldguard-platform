package com.coldguard.commons.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes the minimum business events (an incident was created, a sensor lost connectivity...) as
 * one structured log line each. When called inside a transaction the line is written only after it
 * commits, so a fact that was rolled back never appears in the logs. Attributes must be
 * non-sensitive and correlatable: ids, priority, status; never an address, a token or a payload.
 *
 * <p>The line carries {@code event.name} and each attribute as its own field; the trace and
 * correlation ids come from the logging context.
 */
public class BusinessEventLogger {

  /** Logger name that separates business events from technical logs in Loki and Grafana. */
  static final String LOGGER_NAME = "coldguard.business";

  private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

  public void log(String eventName, Map<String, String> attributes) {
    Map<String, String> copy = new LinkedHashMap<>(attributes);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              write(eventName, copy);
            }
          });
    } else {
      write(eventName, copy);
    }
  }

  private static void write(String eventName, Map<String, String> attributes) {
    var builder = log.atInfo().addKeyValue("event.name", eventName);
    attributes.forEach(builder::addKeyValue);
    builder.log("Business event {}", eventName);
  }
}
