package com.coldguard.asset.application;

import com.coldguard.asset.domain.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

  /**
   * @throws com.coldguard.asset.domain.AlreadyExistsException if the name is taken
   */
  void insert(Organization organization);

  Optional<Organization> findById(UUID id);

  List<Organization> findPage(int page, int size);

  long count();
}
