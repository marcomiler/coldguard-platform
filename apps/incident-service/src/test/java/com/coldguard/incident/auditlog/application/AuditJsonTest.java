package com.coldguard.incident.auditlog.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditJsonTest {

  @Test
  void objectSkipsNullsAndEscapes() {
    assertThat(AuditJson.object("a", "x\"y", "b", null, "c", 3))
        .isEqualTo("{\"a\":\"x\\\"y\",\"c\":\"3\"}");
  }

  @Test
  void jsonObjectsAreKeptAndPlainTextIsWrapped() {
    assertThat(AuditJson.normalise("{\"a\":1}")).isEqualTo("{\"a\":1}");
    assertThat(AuditJson.normalise("roles=AUDITOR")).isEqualTo("{\"summary\":\"roles=AUDITOR\"}");
    assertThat(AuditJson.normalise("{not json")).isEqualTo("{\"summary\":\"{not json\"}");
    assertThat(AuditJson.normalise(null)).isNull();
  }
}
