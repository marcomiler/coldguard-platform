package com.coldguard.commons.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationContextTest {

  @AfterEach
  void cleanUp() {
    CorrelationContext.clear();
  }

  @Test
  void setCurrentAndClear_roundTrip() {
    assertThat(CorrelationContext.current()).isEmpty();

    CorrelationContext.set("corr-1");
    assertThat(CorrelationContext.current()).contains("corr-1");

    CorrelationContext.clear();
    assertThat(CorrelationContext.current()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"corr-1", "abc.DEF_123-x", "550e8400-e29b-41d4-a716-446655440000"})
  void sanitizeOrGenerate_validValue_isKept(String candidate) {
    assertThat(CorrelationContext.sanitizeOrGenerate(candidate)).isEqualTo(candidate);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "has space",
        "semi;colon",
        "<script>",
        "line\nbreak",
        "carriage\rreturn",
        "ünïcode",
        "{jndi:ldap://x}"
      })
  void sanitizeOrGenerate_invalidValue_isReplacedByGeneratedId(String candidate) {
    String result = CorrelationContext.sanitizeOrGenerate(candidate);

    assertThat(result).isNotEqualTo(candidate).matches("[A-Za-z0-9._-]{1,100}");
  }

  @Test
  void sanitizeOrGenerate_tooLongValue_isReplaced() {
    assertThat(CorrelationContext.sanitizeOrGenerate("a".repeat(100))).hasSize(100);
    assertThat(CorrelationContext.sanitizeOrGenerate("a".repeat(101))).hasSize(36);
  }

  @Test
  void newId_isUniqueAndValid() {
    String first = CorrelationContext.newId();
    String second = CorrelationContext.newId();

    assertThat(first).isNotEqualTo(second).matches("[A-Za-z0-9._-]{1,100}");
  }
}
