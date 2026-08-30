package com.coldguard.incident.application;

import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "CRITICAL,IMMEDIATE,P1",
            "CRITICAL,HIGH,P1",
            "CRITICAL,MEDIUM,P2",
            "CRITICAL,LOW,P2",
            "HIGH,IMMEDIATE,P1",
            "HIGH,HIGH,P2",
            "HIGH,MEDIUM,P2",
            "HIGH,LOW,P3",
            "MEDIUM,IMMEDIATE,P2",
            "MEDIUM,HIGH,P3",
            "MEDIUM,MEDIUM,P3",
            "MEDIUM,LOW,P4",
            "LOW,IMMEDIATE,P3",
            "LOW,HIGH,P4",
            "LOW,MEDIUM,P4",
            "LOW,LOW,P4"
    })
    void priorityFrom_coversFullImpactUrgencyMatrix(String impact, String urgency, String expected) {
        Priority result = PriorityCalculator.priorityFrom(Impact.valueOf(impact), Urgency.valueOf(urgency));

        assertThat(result).isEqualTo(Priority.valueOf(expected));
    }

    @Test
    void impactFrom_mapsEachCriticalityLevel() {
        assertThat(PriorityCalculator.impactFrom(Criticality.LOW)).isEqualTo(Impact.LOW);
        assertThat(PriorityCalculator.impactFrom(Criticality.MEDIUM)).isEqualTo(Impact.MEDIUM);
        assertThat(PriorityCalculator.impactFrom(Criticality.HIGH)).isEqualTo(Impact.HIGH);
        assertThat(PriorityCalculator.impactFrom(Criticality.CRITICAL)).isEqualTo(Impact.CRITICAL);
    }

    @Test
    void urgencyFrom_nonPersistent_mapsMagnitudeDirectly() {
        assertThat(PriorityCalculator.urgencyFrom(Magnitude.LOW, false)).isEqualTo(Urgency.LOW);
        assertThat(PriorityCalculator.urgencyFrom(Magnitude.CRITICAL, false)).isEqualTo(Urgency.IMMEDIATE);
    }

    @Test
    void urgencyFrom_persistent_escalatesOneLevelCappedAtImmediate() {
        assertThat(PriorityCalculator.urgencyFrom(Magnitude.LOW, true)).isEqualTo(Urgency.MEDIUM);
        assertThat(PriorityCalculator.urgencyFrom(Magnitude.HIGH, true)).isEqualTo(Urgency.IMMEDIATE);
        assertThat(PriorityCalculator.urgencyFrom(Magnitude.CRITICAL, true)).isEqualTo(Urgency.IMMEDIATE);
    }
}
