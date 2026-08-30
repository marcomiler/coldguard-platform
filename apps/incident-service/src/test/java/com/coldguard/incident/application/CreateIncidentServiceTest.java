package com.coldguard.incident.application;

import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pure unit test: IncidentRepository is mocked, no database involved.
 */
class CreateIncidentServiceTest {

    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final CreateIncidentService service = new CreateIncidentService(incidentRepository);

    @Test
    void create_returnsIncidentWithCalculatedPriority() {
        given(incidentRepository.findOpenIncidentId("asset-1", "sensor-1", "high-temperature"))
                .willReturn(Optional.empty());
        CreateIncidentCommand command = new CreateIncidentCommand(
                "asset-1", Criticality.CRITICAL, "sensor-1", "high-temperature",
                Magnitude.CRITICAL, true, "corr-1");

        Incident incident = service.create(command);

        assertThat(incident.id()).isNotBlank();
        assertThat(incident.assetId()).isEqualTo("asset-1");
        assertThat(incident.sensorId()).isEqualTo("sensor-1");
        assertThat(incident.anomalyType()).isEqualTo("high-temperature");
        assertThat(incident.impact()).isEqualTo(Impact.CRITICAL);
        assertThat(incident.urgency()).isEqualTo(Urgency.IMMEDIATE);
        assertThat(incident.priority()).isEqualTo(Priority.P1);
        assertThat(incident.status()).isEqualTo(IncidentStatus.CREATED);
        verify(incidentRepository).save(incident);
    }

    @Test
    void create_rejectsWhenAnOpenIncidentAlreadyExists() {
        given(incidentRepository.findOpenIncidentId("asset-1", "sensor-1", "high-temperature"))
                .willReturn(Optional.of("existing-id"));
        CreateIncidentCommand command = new CreateIncidentCommand(
                "asset-1", Criticality.MEDIUM, "sensor-1", "high-temperature",
                Magnitude.MEDIUM, false, "corr-1");

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IncidentAlreadyOpenException.class)
                .satisfies(ex -> assertThat(((IncidentAlreadyOpenException) ex).getExistingIncidentId())
                        .isEqualTo("existing-id"));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void create_racedDuplicate_translatesToIncidentAlreadyOpen() {
        given(incidentRepository.findOpenIncidentId("asset-1", "sensor-1", "high-temperature"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of("winner-id"));
        doThrow(new DuplicateIncidentException("duplicate", null)).when(incidentRepository).save(any());
        CreateIncidentCommand command = new CreateIncidentCommand(
                "asset-1", Criticality.MEDIUM, "sensor-1", "high-temperature",
                Magnitude.MEDIUM, false, "corr-1");

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IncidentAlreadyOpenException.class)
                .satisfies(ex -> assertThat(((IncidentAlreadyOpenException) ex).getExistingIncidentId())
                        .isEqualTo("winner-id"));
    }
}
