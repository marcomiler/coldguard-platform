package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentQueryService {

  private final IncidentRepository repository;
  private final IncidentQueryLimits limits;

  IncidentQueryService(IncidentRepository repository, IncidentQueryLimits limits) {
    this.repository = repository;
    this.limits = limits;
  }

  @Transactional(readOnly = true)
  public Incident get(String incidentId) {
    return repository
        .findById(incidentId)
        .orElseThrow(() -> new IncidentNotFoundException(incidentId));
  }

  /**
   * @throws IllegalArgumentException if the page is negative or larger than the configured maximum
   */
  @Transactional(readOnly = true)
  public PageResult<Incident> list(IncidentSearch search, PageQuery page) {
    if (page.page() < 0 || page.size() < 0) {
      throw new IllegalArgumentException("page and size must not be negative");
    }
    if (page.size() > limits.maxSize()) {
      throw new IllegalArgumentException("page size must not exceed " + limits.maxSize());
    }
    int size = page.size() == 0 ? limits.defaultSize() : page.size();
    return repository.search(search, new PageQuery(page.page(), size));
  }
}
