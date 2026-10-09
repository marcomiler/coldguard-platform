package com.coldguard.incident.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityBootstrapTest {

  @Test
  void aConfiguredAddressIsUsedTrimmed() {
    assertThat(
            IdentityBootstrap.demoEmail("supervisor", Map.of("supervisor", " ops@example.test ")))
        .isEqualTo("ops@example.test");
  }

  @Test
  void aMissingOrBlankAddressKeepsTheLocalPlaceholder() {
    assertThat(IdentityBootstrap.demoEmail("operator", Map.of()))
        .isEqualTo("operator@coldguard.local");
    assertThat(IdentityBootstrap.demoEmail("auditor", Map.of("auditor", "  ")))
        .isEqualTo("auditor@coldguard.local");
    assertThat(IdentityBootstrap.demoEmail("admin", null)).isEqualTo("admin@coldguard.local");
  }

  @Test
  void anAddressIsPickedPerUsername() {
    Map<String, String> configured =
        Map.of("supervisor", "a@example.test", "technician", "b@example.test");

    assertThat(IdentityBootstrap.demoEmail("technician", configured)).isEqualTo("b@example.test");
    assertThat(IdentityBootstrap.demoEmail("admin", configured)).isEqualTo("admin@coldguard.local");
  }
}
