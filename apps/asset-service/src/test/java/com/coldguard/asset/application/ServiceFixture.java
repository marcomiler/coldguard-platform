package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.Site;
import com.coldguard.asset.support.InMemoryAssetStore;
import com.coldguard.asset.support.RecordingEvents;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/** The use cases wired over the in-memory store, with a fixed clock. */
class ServiceFixture {

  static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  static final Actor ADMIN = new Actor("admin-1", Set.of(Role.PLATFORM_ADMIN));
  static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));
  static final Actor OPERATOR = new Actor("op-1", Set.of(Role.OPERATOR));

  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  final InMemoryAssetStore store = new InMemoryAssetStore();
  final RecordingEvents events = new RecordingEvents();
  final PageRequestPolicy paging = new PageRequestPolicy(100, 20);

  final AssetCatalogService catalog =
      new AssetCatalogService(
          store.organizationRepository,
          store.siteRepository,
          store.assetRepository,
          events,
          paging,
          clock);
  final OperationalProfileService profiles =
      new OperationalProfileService(
          store.sensorRepository, store.profileRepository, store.historyRepository, events, clock);

  SensorService sensors(Duration defaultValidity) {
    return new SensorService(
        store.assetRepository,
        store.sensorRepository,
        store.calibrationRepository,
        store.historyRepository,
        profiles,
        new CalibrationPolicy(defaultValidity),
        events,
        paging,
        clock);
  }

  Asset anAsset() {
    Organization organization = catalog.createOrganization(ADMIN, "Acme");
    Site site = catalog.createSite(ADMIN, organization.id(), "Main site", null);
    return catalog.registerAsset(ADMIN, site.id(), "Cold room 1", null, Criticality.HIGH);
  }

  static OperationalProfileDraft draft(UUID sensorId, String unit, Duration calibrationValidity) {
    return new OperationalProfileDraft(
        sensorId,
        new BigDecimal("2.0"),
        new BigDecimal("8.0"),
        unit,
        new BigDecimal("1.0"),
        new BigDecimal("3.0"),
        new BigDecimal("6.0"),
        3,
        Duration.ofMinutes(5),
        Duration.ofSeconds(5),
        calibrationValidity);
  }
}
