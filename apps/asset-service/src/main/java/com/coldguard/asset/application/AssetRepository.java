package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {

  void insert(Asset asset);

  Optional<Asset> findById(UUID id);

  /**
   * Persists the asset if its stored version still equals {@code asset.version()} and returns it
   * with the new version.
   *
   * @throws com.coldguard.asset.domain.StaleVersionException otherwise
   */
  Asset update(Asset asset);

  /** {@code siteId} null lists every site's assets. */
  List<Asset> findPage(UUID siteId, int page, int size);

  long count(UUID siteId);
}
