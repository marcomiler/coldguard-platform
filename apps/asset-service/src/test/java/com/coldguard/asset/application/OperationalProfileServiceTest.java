package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static com.coldguard.asset.application.ServiceFixture.draft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.StaleVersionException;
import com.coldguard.asset.support.EventContract;
import com.coldguard.commons.security.Actor;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalProfileServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final Sensor sensor =
      f.sensors(null).register(ADMIN, f.anAsset().id(), "SN-1", null, "CELSIUS", null, null);

  private OperationalProfileDraft baseDraft() {
    return draft(sensor.id(), "CELSIUS", null);
  }

  @Test
  void upsert_createsTheProfileAtVersionOneAndPublishesIt() {
    f.events.published.clear();

    OperationalProfile saved = f.profiles.upsert(ADMIN, baseDraft(), 0);

    assertThat(saved.version()).isEqualTo(1);
    assertThat(saved.updatedBy()).isEqualTo("admin-1");
    var event = f.events.only();
    assertThat(event.eventType()).isEqualTo("OperationalProfileUpdated");
    assertThat(event.routingKey()).isEqualTo("asset.operational-profile-updated");
    assertThat(event.aggregateId()).isEqualTo(sensor.id().toString());
    EventContract.assertConforms(event);
    assertThat(f.store.history.stream().map(h -> h.action())).contains("PROFILE_UPDATED");
  }

  @Test
  void upsert_updatesWithTheExpectedVersionAndCarriesThePreviousValues() {
    f.profiles.upsert(ADMIN, baseDraft(), 0);
    f.events.published.clear();
    OperationalProfileDraft changed =
        new OperationalProfileDraft(
            sensor.id(),
            new BigDecimal("1.0"),
            new BigDecimal("9.0"),
            "CELSIUS",
            new BigDecimal("1.0"),
            new BigDecimal("3.0"),
            new BigDecimal("6.0"),
            3,
            Duration.ofMinutes(5),
            Duration.ofSeconds(5),
            Duration.ofDays(30));

    OperationalProfile saved = f.profiles.upsert(ADMIN, changed, 1);

    assertThat(saved.version()).isEqualTo(2);
    var event = f.events.only();
    EventContract.assertConforms(event);
    var payload =
        tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(event.payload());
    assertThat(payload.get("profileVersion").asLong()).isEqualTo(2);
    assertThat(payload.get("previous").get("minTemperature").decimalValue())
        .isEqualByComparingTo("2.00");
    assertThat(payload.get("current").get("minTemperature").decimalValue())
        .isEqualByComparingTo("1.00");
  }

  @Test
  void upsert_identicalProfileChangesNothing() {
    f.profiles.upsert(ADMIN, baseDraft(), 0);
    f.events.published.clear();
    int historyBefore = f.store.history.size();

    OperationalProfile again = f.profiles.upsert(ADMIN, baseDraft(), 1);

    assertThat(again.version()).isEqualTo(1);
    assertThat(f.events.published).isEmpty();
    assertThat(f.store.history).hasSize(historyBefore);
  }

  @Test
  void upsert_rejectsVersionMismatchesInBothDirections() {
    assertThatThrownBy(() -> f.profiles.upsert(ADMIN, baseDraft(), 3))
        .isInstanceOf(StaleVersionException.class);

    f.profiles.upsert(ADMIN, baseDraft(), 0);
    assertThatThrownBy(() -> f.profiles.upsert(ADMIN, baseDraft(), 0))
        .isInstanceOf(StaleVersionException.class);
    assertThatThrownBy(() -> f.profiles.upsert(ADMIN, baseDraft(), 5))
        .isInstanceOf(StaleVersionException.class);
  }

  @Test
  void upsert_validatesTheProfileAndItsUnit() {
    assertThatThrownBy(() -> f.profiles.upsert(ADMIN, draft(sensor.id(), "FAHRENHEIT", null), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unit");
    assertThatThrownBy(
            () ->
                f.profiles.upsert(
                    ADMIN,
                    new OperationalProfileDraft(
                        sensor.id(),
                        new BigDecimal("8"),
                        new BigDecimal("2"),
                        "CELSIUS",
                        BigDecimal.ONE,
                        BigDecimal.TWO,
                        BigDecimal.TEN,
                        1,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        null),
                    0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(f.store.profiles).isEmpty();
  }

  @Test
  void upsert_needsAnExistingSensor() {
    assertThatThrownBy(() -> f.profiles.upsert(ADMIN, draft(UUID.randomUUID(), "CELSIUS", null), 0))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void upsertRequiresAdminAndGetAdminOrSupervisor() {
    for (Actor caller : new Actor[] {null, SUPERVISOR, OPERATOR}) {
      assertThatThrownBy(() -> f.profiles.upsert(caller, baseDraft(), 0))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
    f.profiles.upsert(ADMIN, baseDraft(), 0);
    assertThat(f.profiles.get(SUPERVISOR, sensor.id()).version()).isEqualTo(1);
    assertThatThrownBy(() -> f.profiles.get(OPERATOR, sensor.id()))
        .isInstanceOf(AssetAccessDeniedException.class);
  }

  @Test
  void get_withoutAProfileIsNotFoundWithItsOwnCode() {
    assertThatThrownBy(() -> f.profiles.get(ADMIN, sensor.id()))
        .isInstanceOf(ResourceNotFoundException.class)
        .extracting(e -> ((ResourceNotFoundException) e).code())
        .isEqualTo("PROFILE_NOT_FOUND");
  }
}
