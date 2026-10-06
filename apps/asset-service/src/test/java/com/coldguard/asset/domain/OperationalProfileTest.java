package com.coldguard.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalProfileTest {

  private static OperationalProfile profile(
      String min,
      String max,
      String medium,
      String high,
      String critical,
      int minConsecutive,
      Duration window,
      Duration interval,
      Duration validity) {
    return new OperationalProfile(
        UUID.randomUUID(),
        new BigDecimal(min),
        new BigDecimal(max),
        "CELSIUS",
        new BigDecimal(medium),
        new BigDecimal(high),
        new BigDecimal(critical),
        minConsecutive,
        window,
        interval,
        validity,
        Instant.parse("2026-10-05T12:00:00Z"),
        "admin-1",
        1);
  }

  private static OperationalProfile valid() {
    return profile(
        "2.0", "8.0", "1.0", "3.0", "6.0", 3, Duration.ofMinutes(5), Duration.ofSeconds(5), null);
  }

  @Test
  void validProfile_keepsTwoDecimalsAndExposesASnapshot() {
    OperationalProfile profile = valid();

    assertThat(profile.minTemperature()).isEqualByComparingTo("2.00");
    assertThat(profile.minTemperature().scale()).isEqualTo(2);
    assertThat(profile.snapshot())
        .containsEntry("unit", "CELSIUS")
        .containsEntry("persistenceWindowSeconds", 300L)
        .containsEntry("expectedIntervalSeconds", 5L)
        .containsEntry("calibrationValiditySeconds", null);
  }

  @Test
  void rangeMustBeStrictlyIncreasing() {
    assertThatThrownBy(
            () ->
                profile(
                    "8.0",
                    "8.0",
                    "1",
                    "3",
                    "6",
                    3,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(5),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minTemperature");
    assertThatThrownBy(
            () ->
                profile(
                    "9.0",
                    "8.0",
                    "1",
                    "3",
                    "6",
                    3,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(5),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void magnitudeBandsMustBePositiveAndStrictlyIncreasing() {
    for (String[] bands :
        new String[][] {
          {"0", "3", "6"}, {"-1", "3", "6"}, {"3", "3", "6"}, {"1", "6", "6"}, {"4", "3", "6"}
        }) {
      assertThatThrownBy(
              () ->
                  profile(
                      "2",
                      "8",
                      bands[0],
                      bands[1],
                      bands[2],
                      3,
                      Duration.ofMinutes(5),
                      Duration.ofSeconds(5),
                      null))
          .as("bands %s", String.join("/", bands))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("magnitude bands");
    }
  }

  @Test
  void persistenceAndIntervalsMustBePositive() {
    assertThatThrownBy(
            () ->
                profile(
                    "2", "8", "1", "3", "6", 0, Duration.ofMinutes(5), Duration.ofSeconds(5), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minConsecutiveBreaches");
    assertThatThrownBy(
            () -> profile("2", "8", "1", "3", "6", 3, Duration.ZERO, Duration.ofSeconds(5), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("window");
    assertThatThrownBy(
            () -> profile("2", "8", "1", "3", "6", 3, Duration.ofMinutes(5), Duration.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expectedReadingInterval");
    assertThatThrownBy(
            () ->
                profile(
                    "2",
                    "8",
                    "1",
                    "3",
                    "6",
                    3,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(5),
                    Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("calibrationValidity");
  }

  @Test
  void valuesBeyondTheColumnRangeAreRejectedBeforeReachingTheDatabase() {
    assertThatThrownBy(
            () ->
                profile(
                    "-10000",
                    "8",
                    "1",
                    "3",
                    "6",
                    3,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(5),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("9999.99");
    assertThatThrownBy(
            () ->
                profile(
                    "2",
                    "10000",
                    "1",
                    "3",
                    "6",
                    3,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(5),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unitAndAuthorAreRequired() {
    assertThatThrownBy(
            () ->
                new OperationalProfile(
                    UUID.randomUUID(),
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    " ",
                    BigDecimal.ONE,
                    BigDecimal.TWO,
                    BigDecimal.TEN,
                    1,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    null,
                    Instant.now(),
                    "admin",
                    1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unit");
  }
}
