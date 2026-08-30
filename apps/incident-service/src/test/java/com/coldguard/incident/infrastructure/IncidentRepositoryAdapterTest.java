package com.coldguard.incident.infrastructure;

import com.coldguard.incident.application.DuplicateIncidentException;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requires a real, reachable PostgreSQL instance with schema/tables managed by Flyway
 * (see deploy/local/docker-compose.yml). Tagged "integration" so it is excluded from the
 * default `mvn test` run (pom.xml surefire configuration); run explicitly once a database is up.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IncidentRepositoryAdapter.class)
class IncidentRepositoryAdapterTest {

    @Autowired
    private IncidentRepositoryAdapter adapter;

    @Test
    void save_thenFindOpenIncidentId_returnsSavedIncidentId() {
        Incident incident = newIncident("asset-it-1", "sensor-it-1", "high-temperature");

        adapter.save(incident);

        Optional<String> found = adapter.findOpenIncidentId("asset-it-1", "sensor-it-1", "high-temperature");
        assertThat(found).contains(incident.id());
    }

    @Test
    void save_duplicateAssetSensorAnomalyType_throwsDuplicateIncidentException() {
        Incident first = newIncident("asset-it-2", "sensor-it-2", "high-temperature");
        adapter.save(first);
        Incident second = newIncident("asset-it-2", "sensor-it-2", "high-temperature");

        assertThatThrownBy(() -> adapter.save(second))
                .isInstanceOf(DuplicateIncidentException.class);
    }

    @Test
    void findOpenIncidentId_noMatch_returnsEmpty() {
        Optional<String> found = adapter.findOpenIncidentId("no-such-asset", "no-such-sensor", "no-such-type");

        assertThat(found).isEmpty();
    }

    private static Incident newIncident(String assetId, String sensorId, String anomalyType) {
        return new Incident(
                UUID.randomUUID().toString(),
                assetId,
                sensorId,
                anomalyType,
                Impact.HIGH,
                Urgency.HIGH,
                Priority.P2,
                IncidentStatus.CREATED,
                Instant.now());
    }
}
