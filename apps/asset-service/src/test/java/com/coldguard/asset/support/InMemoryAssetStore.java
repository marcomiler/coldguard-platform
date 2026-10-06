package com.coldguard.asset.support;

import com.coldguard.asset.application.AssetRepository;
import com.coldguard.asset.application.AssignmentEntry;
import com.coldguard.asset.application.CalibrationRepository;
import com.coldguard.asset.application.EvaluationContextRepository;
import com.coldguard.asset.application.HistoryCursor;
import com.coldguard.asset.application.OperationalProfileRepository;
import com.coldguard.asset.application.OrganizationRepository;
import com.coldguard.asset.application.SensorEvaluationContext;
import com.coldguard.asset.application.SensorHistoryEntry;
import com.coldguard.asset.application.SensorHistoryRepository;
import com.coldguard.asset.application.SensorRepository;
import com.coldguard.asset.application.SiteRepository;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.Site;
import com.coldguard.asset.domain.StaleVersionException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** The repository ports over plain maps, with the same uniqueness and version rules as the SQL. */
public class InMemoryAssetStore {

  public final Map<UUID, Organization> organizations = new LinkedHashMap<>();
  public final Map<UUID, Site> sites = new LinkedHashMap<>();
  public final Map<UUID, Asset> assets = new LinkedHashMap<>();
  public final Map<UUID, Sensor> sensors = new LinkedHashMap<>();
  public final Map<UUID, OperationalProfile> profiles = new LinkedHashMap<>();
  public final List<CalibrationRecord> calibrations = new ArrayList<>();
  public final List<AssignmentEntry> assignments = new ArrayList<>();
  public final List<SensorHistoryEntry> history = new ArrayList<>();

  public final OrganizationRepository organizationRepository = new Organizations();
  public final SiteRepository siteRepository = new Sites();
  public final AssetRepository assetRepository = new Assets();
  public final SensorRepository sensorRepository = new Sensors();
  public final OperationalProfileRepository profileRepository = new Profiles();
  public final CalibrationRepository calibrationRepository = calibrations::add;
  public final SensorHistoryRepository historyRepository =
      new SensorHistoryRepository() {
        @Override
        public void addAssignment(AssignmentEntry entry) {
          assignments.add(entry);
        }

        @Override
        public void addEntry(SensorHistoryEntry entry) {
          history.add(entry);
        }

        @Override
        public List<SensorHistoryEntry> findPage(UUID sensorId, HistoryCursor cursor, int limit) {
          return history.stream()
              .filter(e -> e.sensorId().equals(sensorId))
              .filter(
                  e ->
                      cursor == null
                          || e.occurredAt().isBefore(cursor.occurredAt())
                          || (e.occurredAt().equals(cursor.occurredAt())
                              && e.id().compareTo(cursor.id()) < 0))
              .sorted(
                  Comparator.comparing(SensorHistoryEntry::occurredAt)
                      .thenComparing(SensorHistoryEntry::id)
                      .reversed())
              .limit(limit)
              .toList();
        }
      };

  public final EvaluationContextRepository evaluationContextRepository =
      ids ->
          ids.stream()
              .map(sensors::get)
              .filter(java.util.Objects::nonNull)
              .map(
                  sensor ->
                      new SensorEvaluationContext(
                          sensor.id(),
                          sensor.assetId(),
                          assets.get(sensor.assetId()).criticality(),
                          sensor.status(),
                          profiles.get(sensor.id())))
              .toList();

  private final class Organizations implements OrganizationRepository {
    @Override
    public void insert(Organization organization) {
      if (organizations.values().stream()
          .anyMatch(o -> o.name().equalsIgnoreCase(organization.name()))) {
        throw new AlreadyExistsException("ORGANIZATION_NAME_DUPLICATED", "organization name taken");
      }
      organizations.put(organization.id(), organization);
    }

    @Override
    public Optional<Organization> findById(UUID id) {
      return Optional.ofNullable(organizations.get(id));
    }

    @Override
    public List<Organization> findPage(int page, int size) {
      return pageOf(
          organizations.values(), o -> true, Comparator.comparing(Organization::name), page, size);
    }

    @Override
    public long count() {
      return organizations.size();
    }
  }

  private final class Sites implements SiteRepository {
    @Override
    public void insert(Site site) {
      if (sites.values().stream()
          .anyMatch(
              s ->
                  s.organizationId().equals(site.organizationId())
                      && s.name().equalsIgnoreCase(site.name()))) {
        throw new AlreadyExistsException("SITE_NAME_DUPLICATED", "site name taken");
      }
      sites.put(site.id(), site);
    }

    @Override
    public Optional<Site> findById(UUID id) {
      return Optional.ofNullable(sites.get(id));
    }

    @Override
    public List<Site> findPage(UUID organizationId, int page, int size) {
      return pageOf(
          sites.values(),
          s -> organizationId == null || s.organizationId().equals(organizationId),
          Comparator.comparing(Site::name),
          page,
          size);
    }

    @Override
    public long count(UUID organizationId) {
      return sites.values().stream()
          .filter(s -> organizationId == null || s.organizationId().equals(organizationId))
          .count();
    }
  }

  private final class Assets implements AssetRepository {
    @Override
    public void insert(Asset asset) {
      assets.put(asset.id(), asset);
    }

    @Override
    public Optional<Asset> findById(UUID id) {
      return Optional.ofNullable(assets.get(id));
    }

    @Override
    public Asset update(Asset asset) {
      Asset stored = assets.get(asset.id());
      if (stored == null || stored.version() != asset.version()) {
        throw new StaleVersionException("Asset", asset.id());
      }
      Asset saved = asset.withVersion(asset.version() + 1);
      assets.put(saved.id(), saved);
      return saved;
    }

    @Override
    public List<Asset> findPage(UUID siteId, int page, int size) {
      return pageOf(
          assets.values(),
          a -> siteId == null || a.siteId().equals(siteId),
          Comparator.comparing(Asset::name),
          page,
          size);
    }

    @Override
    public long count(UUID siteId) {
      return assets.values().stream()
          .filter(a -> siteId == null || a.siteId().equals(siteId))
          .count();
    }
  }

  private final class Sensors implements SensorRepository {
    @Override
    public void insert(Sensor sensor) {
      if (sensors.values().stream()
          .anyMatch(s -> s.serialNumber().equalsIgnoreCase(sensor.serialNumber()))) {
        throw new AlreadyExistsException("SENSOR_SERIAL_DUPLICATED", "serial taken");
      }
      sensors.put(sensor.id(), sensor);
    }

    @Override
    public Optional<Sensor> findById(UUID id) {
      return Optional.ofNullable(sensors.get(id));
    }

    @Override
    public Sensor update(Sensor sensor) {
      Sensor stored = sensors.get(sensor.id());
      if (stored == null || stored.version() != sensor.version()) {
        throw new StaleVersionException("Sensor", sensor.id());
      }
      if (sensors.values().stream()
          .anyMatch(
              s ->
                  !s.id().equals(sensor.id())
                      && s.serialNumber().equalsIgnoreCase(sensor.serialNumber()))) {
        throw new AlreadyExistsException("SENSOR_SERIAL_DUPLICATED", "serial taken");
      }
      Sensor saved = sensor.withVersion(sensor.version() + 1);
      sensors.put(saved.id(), saved);
      return saved;
    }

    @Override
    public List<Sensor> findPage(UUID assetId, SensorStatus status, int page, int size) {
      return pageOf(
          sensors.values(),
          s ->
              (assetId == null || s.assetId().equals(assetId))
                  && (status == null || s.status() == status),
          Comparator.comparing(Sensor::serialNumber),
          page,
          size);
    }

    @Override
    public long count(UUID assetId, SensorStatus status) {
      return sensors.values().stream()
          .filter(
              s ->
                  (assetId == null || s.assetId().equals(assetId))
                      && (status == null || s.status() == status))
          .count();
    }
  }

  private final class Profiles implements OperationalProfileRepository {
    @Override
    public Optional<OperationalProfile> findBySensorId(UUID sensorId) {
      return Optional.ofNullable(profiles.get(sensorId));
    }

    @Override
    public OperationalProfile save(OperationalProfile profile, long expectedVersion) {
      OperationalProfile stored = profiles.get(profile.sensorId());
      long storedVersion = stored == null ? 0 : stored.version();
      if (storedVersion != expectedVersion) {
        throw new StaleVersionException("OperationalProfile", profile.sensorId());
      }
      OperationalProfile saved = profile.withVersion(expectedVersion + 1);
      profiles.put(saved.sensorId(), saved);
      return saved;
    }
  }

  private static <T> List<T> pageOf(
      Iterable<T> source, Predicate<T> filter, Comparator<T> order, int page, int size) {
    List<T> all = new ArrayList<>();
    source.forEach(
        item -> {
          if (filter.test(item)) {
            all.add(item);
          }
        });
    all.sort(order);
    return all.stream().skip((long) page * size).limit(size).toList();
  }
}
