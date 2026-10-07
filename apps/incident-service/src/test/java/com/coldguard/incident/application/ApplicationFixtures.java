package com.coldguard.incident.application;

import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.auditlog.application.AuditEntry;
import com.coldguard.incident.auditlog.application.AuditRecorder;
import com.coldguard.incident.domain.Incident;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory collaborators for use-case tests; no broker or database. */
final class ApplicationFixtures {

  static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));
  static final Actor TECHNICIAN = new Actor("tech-1", Set.of(Role.MAINTENANCE_TECHNICIAN));

  private ApplicationFixtures() {}

  static class InMemoryIncidents implements IncidentRepository {
    final Map<String, Incident> byId = new LinkedHashMap<>();
    DuplicateIncidentException failNextSave;

    @Override
    public void save(Incident incident) {
      if (failNextSave != null) {
        DuplicateIncidentException ex = failNextSave;
        failNextSave = null;
        throw ex;
      }
      byId.put(incident.id(), incident);
    }

    @Override
    public Optional<Incident> findOpen(String assetId, String sensorId, String anomalyType) {
      return byId.values().stream()
          .filter(i -> i.status().isOpen())
          .filter(
              i ->
                  i.assetId().equals(assetId)
                      && i.sensorId().equals(sensorId)
                      && i.anomalyType().equals(anomalyType))
          .findFirst();
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
      return Optional.ofNullable(byId.get(incidentId));
    }

    @Override
    public void update(Incident incident) {
      byId.put(incident.id(), incident);
    }

    @Override
    public PageResult<Incident> search(IncidentSearch search, PageQuery page) {
      List<Incident> all = new ArrayList<>(byId.values());
      return new PageResult<>(all, page.page(), page.size(), all.size());
    }
  }

  static final class CapturedEvents implements DomainEventPublisher {
    final List<OutboundEvent> published = new ArrayList<>();

    @Override
    public void publish(OutboundEvent event) {
      published.add(event);
    }

    List<String> types() {
      return published.stream().map(OutboundEvent::eventType).toList();
    }
  }

  static final class CapturedAudit implements AuditRecorder {
    final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void record(AuditEntry entry) {
      entries.add(entry);
    }

    List<String> actions() {
      return entries.stream().map(AuditEntry::action).toList();
    }
  }

  static final class FixedRecipients implements NotificationRecipients {
    final Map<Role, List<Recipient>> byRole = new LinkedHashMap<>();

    FixedRecipients() {
      byRole.put(Role.OPERATIONS_SUPERVISOR, List.of(new Recipient("sup-1", "sup@coldguard.test")));
      byRole.put(
          Role.MAINTENANCE_TECHNICIAN, List.of(new Recipient("tech-1", "tech@coldguard.test")));
    }

    @Override
    public List<Recipient> enabledWithRole(Role role) {
      return byRole.getOrDefault(role, List.of());
    }
  }
}
