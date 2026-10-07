package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.Priority;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SLA targets per priority (a missing {@code resolve} means no resolution target) and listing
 * bounds. Values are academic placeholders until the business confirms them.
 */
@ConfigurationProperties("coldguard.incident")
record IncidentProperties(Map<Priority, Target> sla, Page page) {

  record Target(Duration ack, Duration resolve) {}

  record Page(Integer defaultSize, Integer maxSize) {}

  IncidentProperties {
    sla = sla == null ? Map.of() : Map.copyOf(sla);
    page = page == null ? new Page(null, null) : page;
  }

  int defaultPageSize() {
    return page.defaultSize() == null ? 20 : page.defaultSize();
  }

  int maxPageSize() {
    return page.maxSize() == null ? 100 : page.maxSize();
  }
}
