package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Site;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organizations, sites and assets (cold units). Commands are administrator-only and publish their
 * event through the outbox in the same transaction as the change.
 */
@Service
public class AssetCatalogService {

  private final OrganizationRepository organizations;
  private final SiteRepository sites;
  private final AssetRepository assets;
  private final DomainEventPublisher events;
  private final PageRequestPolicy paging;
  private final Clock clock;

  public AssetCatalogService(
      OrganizationRepository organizations,
      SiteRepository sites,
      AssetRepository assets,
      DomainEventPublisher events,
      PageRequestPolicy paging,
      Clock clock) {
    this.organizations = organizations;
    this.sites = sites;
    this.assets = assets;
    this.events = events;
    this.paging = paging;
    this.clock = clock;
  }

  @Transactional
  public Organization createOrganization(Actor actor, String name) {
    AccessPolicy.requireAdministrator(actor);
    Organization organization = Organization.create(name, clock.instant());
    organizations.insert(organization);
    return organization;
  }

  @Transactional(readOnly = true)
  public Page<Organization> listOrganizations(Actor actor, int page, int requestedSize) {
    AccessPolicy.requireReader(actor);
    int size = paging.sizeFor(requestedSize);
    paging.validate(page, size);
    return new Page<>(organizations.findPage(page, size), page, size, organizations.count());
  }

  @Transactional
  public Site createSite(Actor actor, UUID organizationId, String name, String address) {
    AccessPolicy.requireAdministrator(actor);
    organizations
        .findById(organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    Site site = Site.create(organizationId, name, address, clock.instant());
    sites.insert(site);
    return site;
  }

  @Transactional(readOnly = true)
  public Page<Site> listSites(Actor actor, UUID organizationId, int page, int requestedSize) {
    AccessPolicy.requireReader(actor);
    int size = paging.sizeFor(requestedSize);
    paging.validate(page, size);
    return new Page<>(
        sites.findPage(organizationId, page, size), page, size, sites.count(organizationId));
  }

  @Transactional
  public Asset registerAsset(
      Actor actor, UUID siteId, String name, String description, Criticality criticality) {
    AccessPolicy.requireAdministrator(actor);
    sites.findById(siteId).orElseThrow(() -> new ResourceNotFoundException("Site", siteId));
    Instant now = clock.instant();
    Asset asset = Asset.register(siteId, name, description, criticality, now);
    assets.insert(asset);
    events.publish(AssetEvents.assetRegistered(asset, Actors.toEventActor(actor), now));
    return asset;
  }

  /**
   * Partial update; a null argument leaves that field as it is. An update that changes nothing
   * writes nothing and publishes nothing.
   *
   * @throws com.coldguard.asset.domain.StaleVersionException if {@code expectedVersion} is not the
   *     current version
   */
  @Transactional
  public Asset updateAsset(
      Actor actor,
      UUID assetId,
      long expectedVersion,
      String name,
      String description,
      Criticality criticality) {
    AccessPolicy.requireAdministrator(actor);
    Asset before = find(assetId);
    if (before.version() != expectedVersion) {
      throw new com.coldguard.asset.domain.StaleVersionException("Asset", assetId);
    }
    Instant now = clock.instant();
    Asset candidate = before.update(name, description, criticality, now);
    List<String> changed = candidate.changedFieldsFrom(before);
    if (changed.isEmpty()) {
      return before;
    }
    Asset saved = assets.update(candidate);
    events.publish(
        AssetEvents.assetUpdated(before, saved, changed, Actors.toEventActor(actor), now));
    return saved;
  }

  @Transactional(readOnly = true)
  public Asset getAsset(Actor actor, UUID assetId) {
    AccessPolicy.requireReader(actor);
    return find(assetId);
  }

  @Transactional(readOnly = true)
  public Page<Asset> listAssets(Actor actor, UUID siteId, int page, int requestedSize) {
    AccessPolicy.requireReader(actor);
    int size = paging.sizeFor(requestedSize);
    paging.validate(page, size);
    return new Page<>(assets.findPage(siteId, page, size), page, size, assets.count(siteId));
  }

  private Asset find(UUID assetId) {
    return assets
        .findById(assetId)
        .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
  }
}
