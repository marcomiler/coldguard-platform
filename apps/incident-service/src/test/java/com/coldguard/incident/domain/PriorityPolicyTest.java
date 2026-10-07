package com.coldguard.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriorityPolicyTest {

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
    Priority result = PriorityPolicy.priorityFrom(Impact.valueOf(impact), Urgency.valueOf(urgency));

    assertThat(result).isEqualTo(Priority.valueOf(expected));
  }

  @Test
  void impactFrom_mapsEachCriticalityLevel() {
    assertThat(PriorityPolicy.impactFrom(Criticality.LOW)).isEqualTo(Impact.LOW);
    assertThat(PriorityPolicy.impactFrom(Criticality.MEDIUM)).isEqualTo(Impact.MEDIUM);
    assertThat(PriorityPolicy.impactFrom(Criticality.HIGH)).isEqualTo(Impact.HIGH);
    assertThat(PriorityPolicy.impactFrom(Criticality.CRITICAL)).isEqualTo(Impact.CRITICAL);
  }

  @Test
  void urgencyFrom_nonPersistent_mapsMagnitudeDirectly() {
    assertThat(PriorityPolicy.urgencyFrom(Magnitude.LOW, false)).isEqualTo(Urgency.LOW);
    assertThat(PriorityPolicy.urgencyFrom(Magnitude.CRITICAL, false)).isEqualTo(Urgency.IMMEDIATE);
  }

  @Test
  void urgencyFrom_persistent_escalatesOneLevelCappedAtImmediate() {
    assertThat(PriorityPolicy.urgencyFrom(Magnitude.LOW, true)).isEqualTo(Urgency.MEDIUM);
    assertThat(PriorityPolicy.urgencyFrom(Magnitude.HIGH, true)).isEqualTo(Urgency.IMMEDIATE);
    assertThat(PriorityPolicy.urgencyFrom(Magnitude.CRITICAL, true)).isEqualTo(Urgency.IMMEDIATE);
  }
}
