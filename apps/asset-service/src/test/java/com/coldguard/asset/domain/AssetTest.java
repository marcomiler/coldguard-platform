package com.coldguard.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetTest {

  private static final Instant T0 = Instant.parse("2026-10-05T12:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);

  private final Asset asset =
      Asset.register(UUID.randomUUID(), "  Cold room 1 ", "Vaccines", Criticality.HIGH, T0);

  @Test
  void register_normalizesTextAndStartsAtVersionOne() {
    assertThat(asset.name()).isEqualTo("Cold room 1");
    assertThat(asset.version()).isEqualTo(1);
    assertThat(asset.createdAt()).isEqualTo(T0).isEqualTo(asset.updatedAt());
  }

  @Test
  void criticalityAndNameAreMandatory() {
    assertThatThrownBy(() -> Asset.register(UUID.randomUUID(), "x", null, null, T0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("criticality");
    assertThatThrownBy(() -> Asset.register(UUID.randomUUID(), " ", null, Criticality.LOW, T0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
    assertThatThrownBy(
            () -> Asset.register(UUID.randomUUID(), "x".repeat(121), null, Criticality.LOW, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void update_nullKeepsTheFieldAndBlankDescriptionClearsIt() {
    Asset renamed = asset.update("Cold room 2", null, null, T1);
    assertThat(renamed.name()).isEqualTo("Cold room 2");
    assertThat(renamed.description()).isEqualTo("Vaccines");
    assertThat(renamed.criticality()).isEqualTo(Criticality.HIGH);
    assertThat(renamed.updatedAt()).isEqualTo(T1);

    assertThat(asset.update(null, " ", null, T1).description()).isNull();
  }

  @Test
  void changedFieldsFrom_listsOnlyWhatDiffers() {
    assertThat(asset.update(null, null, null, T1).changedFieldsFrom(asset)).isEmpty();
    assertThat(asset.update("New", null, Criticality.CRITICAL, T1).changedFieldsFrom(asset))
        .containsExactly("name", "criticality");
    assertThat(asset.update(null, "Other", null, T1).changedFieldsFrom(asset))
        .containsExactly("description");
  }
}
