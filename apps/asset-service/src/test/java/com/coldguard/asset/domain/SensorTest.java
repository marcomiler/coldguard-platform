package com.coldguard.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorTest {

  private static final Instant T0 = Instant.parse("2026-10-05T12:00:00Z");

  private final Sensor sensor =
      Sensor.register(UUID.randomUUID(), " SN-1 ", "Model X", "CELSIUS", T0);

  @Test
  void register_startsActiveWithoutCalibration() {
    assertThat(sensor.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(sensor.serialNumber()).isEqualTo("SN-1");
    assertThat(sensor.statusChangedAt()).isEqualTo(T0);
    assertThat(sensor.lastCalibrationRecordedAt()).isNull();
    assertThat(sensor.lastCalibrationValidUntil()).isNull();
    assertThat(sensor.version()).isEqualTo(1);
  }

  @Test
  void serialAndUnitAreMandatory() {
    assertThatThrownBy(() -> Sensor.register(UUID.randomUUID(), "", null, "CELSIUS", T0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serialNumber");
    assertThatThrownBy(() -> Sensor.register(UUID.randomUUID(), "SN", null, " ", T0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("measurementUnit");
  }

  @Test
  void withTechnicalData_changesOnlyWhatIsGiven() {
    Sensor updated = sensor.withTechnicalData(null, "Model Y", T0.plusSeconds(1));

    assertThat(updated.serialNumber()).isEqualTo("SN-1");
    assertThat(updated.model()).isEqualTo("Model Y");
    assertThat(updated.status()).isEqualTo(SensorStatus.ACTIVE);
  }

  @Test
  void withCalibration_neverChangesTheStatus() {
    Sensor calibrated =
        sensor.withCalibration(T0.plusSeconds(5), T0.plusSeconds(500), T0.plusSeconds(5));

    assertThat(calibrated.lastCalibrationRecordedAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(calibrated.lastCalibrationValidUntil()).isEqualTo(T0.plusSeconds(500));
    assertThat(calibrated.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(calibrated.statusChangedAt()).isEqualTo(T0);
  }
}
