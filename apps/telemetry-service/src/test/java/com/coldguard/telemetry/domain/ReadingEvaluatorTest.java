package com.coldguard.telemetry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReadingEvaluatorTest {

  private static EvaluationProfile profile(String min, String max) {
    return new EvaluationProfile(
        new BigDecimal(min),
        new BigDecimal(max),
        "CELSIUS",
        new BigDecimal("1.0"),
        new BigDecimal("3.0"),
        new BigDecimal("6.0"),
        3,
        Duration.ofMinutes(5),
        Duration.ofSeconds(5));
  }

  private static final EvaluationProfile FRIDGE = profile("2.0", "8.0");

  private static Anomaly evaluate(String value, EvaluationProfile profile) {
    return ReadingEvaluator.evaluate(new BigDecimal(value), profile).orElse(null);
  }

  @Test
  void aReadingWithinTheRangeIsNotAnAnomalyBoundsIncluded() {
    for (String value : new String[] {"2.0", "5.5", "8.0", "2.000", "8.000"}) {
      assertThat(evaluate(value, FRIDGE)).as(value).isNull();
    }
  }

  @Test
  void aboveTheMaximumTheDeviationIsMeasuredFromTheMaximum() {
    Anomaly anomaly = evaluate("9.5", FRIDGE);

    assertThat(anomaly.type()).isEqualTo(AnomalyType.TEMPERATURE_ABOVE_MAX);
    assertThat(anomaly.deviation()).isEqualByComparingTo("1.5");
  }

  @Test
  void belowTheMinimumTheDeviationIsMeasuredFromTheMinimum() {
    Anomaly anomaly = evaluate("0.5", FRIDGE);

    assertThat(anomaly.type()).isEqualTo(AnomalyType.TEMPERATURE_BELOW_MIN);
    assertThat(anomaly.deviation()).isEqualByComparingTo("1.5");
  }

  @Test
  void aDeviationThatReachesABandBelongsToThatBand() {
    // bands 1 / 3 / 6 above the maximum of 8
    assertThat(evaluate("8.001", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.LOW);
    assertThat(evaluate("8.999", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.LOW);
    assertThat(evaluate("9.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.MEDIUM);
    assertThat(evaluate("10.999", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.MEDIUM);
    assertThat(evaluate("11.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.HIGH);
    assertThat(evaluate("13.999", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.HIGH);
    assertThat(evaluate("14.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.CRITICAL);
    assertThat(evaluate("40.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.CRITICAL);
  }

  @Test
  void theSameBandsApplyBelowTheMinimum() {
    assertThat(evaluate("1.500", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.LOW);
    assertThat(evaluate("1.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.MEDIUM);
    assertThat(evaluate("-1.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.HIGH);
    assertThat(evaluate("-4.000", FRIDGE).magnitude()).isEqualTo(MagnitudeLevel.CRITICAL);
  }

  @Test
  void aFreezerWithANegativeRangeIsEvaluatedTheSameWay() {
    EvaluationProfile freezer = profile("-25.0", "-15.0");

    assertThat(evaluate("-20.0", freezer)).isNull();
    Anomaly warm = evaluate("-12.0", freezer);
    assertThat(warm.type()).isEqualTo(AnomalyType.TEMPERATURE_ABOVE_MAX);
    assertThat(warm.deviation()).isEqualByComparingTo("3.0");
    assertThat(warm.magnitude()).isEqualTo(MagnitudeLevel.HIGH);
    Anomaly cold = evaluate("-26.0", freezer);
    assertThat(cold.type()).isEqualTo(AnomalyType.TEMPERATURE_BELOW_MIN);
    assertThat(cold.magnitude()).isEqualTo(MagnitudeLevel.MEDIUM);
  }
}
