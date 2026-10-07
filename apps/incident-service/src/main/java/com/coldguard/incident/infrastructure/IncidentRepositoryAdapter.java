package com.coldguard.incident.infrastructure;

import com.coldguard.incident.application.ConcurrentIncidentUpdateException;
import com.coldguard.incident.application.DuplicateIncidentException;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.application.IncidentSearch;
import com.coldguard.incident.application.PageQuery;
import com.coldguard.incident.application.PageResult;
import com.coldguard.incident.domain.Incident;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepositoryAdapter implements IncidentRepository {

  private final IncidentJpaRepository jpaRepository;

  public IncidentRepositoryAdapter(IncidentJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(Incident incident) {
    try {
      jpaRepository.saveAndFlush(IncidentPersistenceMapper.toNewEntity(incident));
    } catch (DataIntegrityViolationException ex) {
      throw new DuplicateIncidentException(
          "An open incident already exists for this asset/sensor/anomaly type", ex);
    }
  }

  @Override
  public Optional<Incident> findOpen(String assetId, String sensorId, String anomalyType) {
    return jpaRepository
        .findOpen(assetId, sensorId, anomalyType)
        .map(IncidentPersistenceMapper::toDomain);
  }

  @Override
  public Optional<Incident> findById(String incidentId) {
    UUID id;
    try {
      id = UUID.fromString(incidentId);
    } catch (IllegalArgumentException notAnId) {
      // Every stored id is a UUID, so anything else cannot exist; it is not a server error.
      return Optional.empty();
    }
    return jpaRepository.findById(id).map(IncidentPersistenceMapper::toDomain);
  }

  /**
   * Applies the new state to the managed row, so the version check and the update are one
   * statement. A stale read (the incident's version differs from the stored one) is rejected here;
   * a race between two transactions surfaces as an optimistic-locking failure on flush.
   */
  @Override
  public void update(Incident incident) {
    IncidentEntity entity =
        jpaRepository
            .findById(UUID.fromString(incident.id()))
            .orElseThrow(() -> new IllegalStateException("Incident vanished: " + incident.id()));
    if (incident.version() != null && !incident.version().equals(entity.version)) {
      throw new ConcurrentIncidentUpdateException(incident.id(), null);
    }
    IncidentPersistenceMapper.copyState(incident, entity);
    try {
      jpaRepository.saveAndFlush(entity);
    } catch (OptimisticLockingFailureException ex) {
      throw new ConcurrentIncidentUpdateException(incident.id(), ex);
    }
  }

  @Override
  public PageResult<Incident> search(IncidentSearch search, PageQuery page) {
    Page<IncidentEntity> result =
        jpaRepository.findAll(
            matching(search),
            PageRequest.of(
                page.page(),
                page.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    return new PageResult<>(
        result.getContent().stream().map(IncidentPersistenceMapper::toDomain).toList(),
        page.page(),
        page.size(),
        result.getTotalElements());
  }

  private static Specification<IncidentEntity> matching(IncidentSearch search) {
    return (root, query, cb) -> {
      List<Predicate> all = new ArrayList<>();
      if (!search.statuses().isEmpty()) {
        all.add(root.get("status").in(search.statuses()));
      }
      if (!search.priorities().isEmpty()) {
        all.add(root.get("priority").in(search.priorities()));
      }
      if (search.assetId() != null && !search.assetId().isBlank()) {
        all.add(cb.equal(root.get("assetId"), search.assetId()));
      }
      if (search.sensorId() != null && !search.sensorId().isBlank()) {
        all.add(cb.equal(root.get("sensorId"), search.sensorId()));
      }
      if (search.createdFrom() != null) {
        all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), search.createdFrom()));
      }
      if (search.createdTo() != null) {
        all.add(cb.lessThan(root.get("createdAt"), search.createdTo()));
      }
      return cb.and(all.toArray(Predicate[]::new));
    };
  }
}
