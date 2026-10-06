package com.coldguard.asset.application;

import com.coldguard.asset.domain.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository {

  /**
   * @throws com.coldguard.asset.domain.AlreadyExistsException if the organization already has a
   *     site with that name
   */
  void insert(Site site);

  Optional<Site> findById(UUID id);

  /** {@code organizationId} null lists every organization's sites. */
  List<Site> findPage(UUID organizationId, int page, int size);

  long count(UUID organizationId);
}
