package com.coldguard.incident.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.incident.application.ConcurrentIncidentUpdateException;
import com.coldguard.incident.application.DuplicateIncidentException;
import com.coldguard.incident.application.IncidentSearch;
import com.coldguard.incident.application.PageQuery;
import com.coldguard.incident.application.PageResult;
import com.coldguard.incident.domain.CloseEvidence;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.DomainFixtures;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requires a real, reachable PostgreSQL instance with schema/tables managed by Flyway (see
 * deploy/local/docker-compose.yml). Tagged "integration" so it is excluded from the default `mvn
 * test` run (pom.xml surefire configuration); run explicitly once a database is up.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IncidentRepositoryAdapter.class)
class IncidentRepositoryAdapterTest {

  @Autowired private IncidentRepositoryAdapter adapter;

  @Test
  void save_thenFindOpen_returnsSavedIncident() {
    Incident incident = newIncident("asset-it-1", "sensor-it-1", "high-temperature");

    adapter.save(incident);

    Optional<Incident> found = adapter.findOpen("asset-it-1", "sensor-it-1", "high-temperature");
    assertThat(found).map(Incident::id).contains(incident.id());
  }

  @Test
  void save_duplicateAssetSensorAnomalyType_throwsDuplicateIncidentException() {
    Incident first = newIncident("asset-it-2", "sensor-it-2", "high-temperature");
    adapter.save(first);
    Incident second = newIncident("asset-it-2", "sensor-it-2", "high-temperature");

    assertThatThrownBy(() -> adapter.save(second)).isInstanceOf(DuplicateIncidentException.class);
  }

  @Test
  void findOpen_noMatch_returnsEmpty() {
    Optional<Incident> found = adapter.findOpen("no-such-asset", "no-such-sensor", "no-such-type");

    assertThat(found).isEmpty();
  }

  @Test
  void closeThenRecreate_sameAssetSensorAnomalyType_succeeds() {
    Incident first = newIncident("asset-it-3", "sensor-it-3", "high-temperature");
    adapter.save(first);

    adapter.update(closed(first));

    Incident second = newIncident("asset-it-3", "sensor-it-3", "high-temperature");
    adapter.save(second);

    Optional<Incident> found = adapter.findOpen("asset-it-3", "sensor-it-3", "high-temperature");
    assertThat(found).map(Incident::id).contains(second.id());
  }

  @Test
  void findOpen_closedIncident_returnsEmpty() {
    Incident incident = newIncident("asset-it-4", "sensor-it-4", "high-temperature");
    adapter.save(incident);

    adapter.update(closed(incident));

    Optional<Incident> found = adapter.findOpen("asset-it-4", "sensor-it-4", "high-temperature");
    assertThat(found).isEmpty();
  }

  /**
   * @DataJpaTest wraps each test in one shared transaction/connection by default, which is not
   * thread-safe and would serialize these "concurrent" calls into a single Session. Disabling that
   * wrapping here gives each thread its own real transaction/connection against Postgres.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void save_concurrentCreatedForSameTuple_onlyOneSucceeds() throws InterruptedException {
    // Not wrapped in a rolled-back transaction (see annotation above): use a unique
    // tuple per
    // run so this test's committed rows never collide with a previous run's
    // leftovers.
    String assetId = "asset-it-5-" + UUID.randomUUID();
    String sensorId = "sensor-it-5";
    String anomalyType = "high-temperature";
    int attempts = 5;
    ExecutorService executor = Executors.newFixedThreadPool(attempts);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      List<Future<Boolean>> results =
          IntStream.range(0, attempts)
              .<Future<Boolean>>mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            startLatch.await();
                            try {
                              adapter.save(newIncident(assetId, sensorId, anomalyType));
                              return true;
                            } catch (DuplicateIncidentException _) {
                              return false;
                            }
                          }))
              .toList();
      startLatch.countDown();

      long successCount =
          results.stream()
              .map(
                  future -> {
                    try {
                      return future.get(10, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                      throw new RuntimeException(ex);
                    }
                  })
              .filter(Boolean::booleanValue)
              .count();

      assertThat(successCount).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void save_duplicateWhileAcknowledgedOrEscalated_stillRejected() {
    Incident first = newIncident("asset-it-6", "sensor-it-6", "high-temperature");
    adapter.save(first);
    adapter.update(first.acknowledge("sup-1", Clock.systemUTC()).incident());

    assertThatThrownBy(
            () -> adapter.save(newIncident("asset-it-6", "sensor-it-6", "high-temperature")))
        .isInstanceOf(DuplicateIncidentException.class);
  }

  @Test
  void update_persistsTheWholeLifecycleAndEvidence() {
    Incident incident = newIncident("asset-it-7", "sensor-it-7", "high-temperature");
    adapter.save(incident);
    Incident stored = adapter.findById(incident.id()).orElseThrow();
    Incident escalated = stored.escalate("no response", Clock.systemUTC()).incident();
    adapter.update(escalated);
    Incident acknowledged =
        adapter
            .findById(incident.id())
            .orElseThrow()
            .acknowledge("sup-1", Clock.systemUTC())
            .incident();
    adapter.update(acknowledged);
    Incident closedIncident =
        adapter
            .findById(incident.id())
            .orElseThrow()
            .close("tech-1", new CloseEvidence("overheating", "replaced"), Clock.systemUTC())
            .incident();
    adapter.update(closedIncident);

    Incident reloaded = adapter.findById(incident.id()).orElseThrow();
    assertThat(reloaded.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(reloaded.escalationCount()).isEqualTo(1);
    assertThat(reloaded.acknowledgedBy()).isEqualTo("sup-1");
    assertThat(reloaded.cause()).isEqualTo("overheating");
    assertThat(reloaded.resolutionComment()).isEqualTo("replaced");
    assertThat(reloaded.closedBy()).isEqualTo("tech-1");
    assertThat(reloaded.closedAt()).isNotNull();
    assertThat(reloaded.ackDueAt()).isNotNull();
  }

  @Test
  void update_staleVersion_isRejectedAsConcurrentModification() {
    Incident incident = newIncident("asset-it-8", "sensor-it-8", "high-temperature");
    adapter.save(incident);
    Incident first = adapter.findById(incident.id()).orElseThrow();
    adapter.update(first.escalate("one", Clock.systemUTC()).incident());
    flush();

    // `first` still carries the version read before the update above.
    assertThatThrownBy(() -> adapter.update(first.escalate("two", Clock.systemUTC()).incident()))
        .isInstanceOf(ConcurrentIncidentUpdateException.class);
  }

  @Test
  void search_filtersByStatusPriorityAndAsset() {
    Incident incident = newIncident("asset-it-9", "sensor-it-9", "high-temperature");
    adapter.save(incident);

    PageResult<Incident> hit =
        adapter.search(
            new IncidentSearch(
                Set.of(IncidentStatus.CREATED),
                Set.of(incident.priority()),
                "asset-it-9",
                null,
                null,
                null),
            new PageQuery(0, 10));
    PageResult<Incident> miss =
        adapter.search(
            new IncidentSearch(Set.of(IncidentStatus.CLOSED), null, "asset-it-9", null, null, null),
            new PageQuery(0, 10));

    assertThat(hit.items()).extracting(Incident::id).containsExactly(incident.id());
    assertThat(hit.totalElements()).isEqualTo(1);
    assertThat(miss.items()).isEmpty();
  }

  @Autowired private jakarta.persistence.EntityManager entityManager;

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  private static Incident closed(Incident incident) {
    return incident
        .close("tech-1", new CloseEvidence("cause", "comment"), Clock.systemUTC())
        .incident();
  }

  private static Incident newIncident(String assetId, String sensorId, String anomalyType) {
    return Incident.open(
            UUID.randomUUID().toString(),
            assetId,
            sensorId,
            anomalyType,
            Criticality.HIGH,
            Magnitude.HIGH,
            false,
            null,
            DomainFixtures.SLA,
            Clock.systemUTC())
        .incident();
  }
}
