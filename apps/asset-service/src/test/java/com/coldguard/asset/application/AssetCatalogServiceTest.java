package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.Organization;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Site;
import com.coldguard.asset.domain.StaleVersionException;
import com.coldguard.asset.support.EventContract;
import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.security.Actor;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetCatalogServiceTest {

  private final ServiceFixture f = new ServiceFixture();

  @Test
  void commandsRequireThePlatformAdminRole() {
    Organization org = f.catalog.createOrganization(ADMIN, "Acme");
    for (Actor caller : new Actor[] {null, SUPERVISOR, OPERATOR, Actor.system("job")}) {
      assertThatThrownBy(() -> f.catalog.createOrganization(caller, "Other"))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> f.catalog.createSite(caller, org.id(), "Site", null))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(
              () -> f.catalog.registerAsset(caller, UUID.randomUUID(), "A", null, Criticality.LOW))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> f.catalog.updateAsset(caller, UUID.randomUUID(), 1, "A", null, null))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
    assertThat(f.store.organizations).hasSize(1);
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void queriesAdmitAdministratorsAndSupervisorsOnly() {
    Asset asset = f.anAsset();

    for (Actor reader : new Actor[] {ADMIN, SUPERVISOR}) {
      assertThat(f.catalog.getAsset(reader, asset.id())).isEqualTo(asset);
      assertThat(f.catalog.listAssets(reader, null, 0, 0).totalElements()).isEqualTo(1);
      assertThat(f.catalog.listOrganizations(reader, 0, 0).items()).hasSize(1);
      assertThat(f.catalog.listSites(reader, null, 0, 0).items()).hasSize(1);
    }
    for (Actor denied : new Actor[] {null, OPERATOR}) {
      assertThatThrownBy(() -> f.catalog.getAsset(denied, asset.id()))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> f.catalog.listAssets(denied, null, 0, 0))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
  }

  @Test
  void aSiteNeedsAnExistingOrganization() {
    assertThatThrownBy(() -> f.catalog.createSite(ADMIN, UUID.randomUUID(), "Site", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .extracting(e -> ((ResourceNotFoundException) e).code())
        .isEqualTo("ORGANIZATION_NOT_FOUND");
  }

  @Test
  void anAssetNeedsAnExistingSite() {
    assertThatThrownBy(
            () -> f.catalog.registerAsset(ADMIN, UUID.randomUUID(), "A", null, Criticality.LOW))
        .isInstanceOf(ResourceNotFoundException.class)
        .extracting(e -> ((ResourceNotFoundException) e).code())
        .isEqualTo("SITE_NOT_FOUND");
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void duplicateNamesAreRejected() {
    Organization org = f.catalog.createOrganization(ADMIN, "Acme");
    f.catalog.createSite(ADMIN, org.id(), "Main", null);

    assertThatThrownBy(() -> f.catalog.createOrganization(ADMIN, "ACME"))
        .isInstanceOf(AlreadyExistsException.class);
    assertThatThrownBy(() -> f.catalog.createSite(ADMIN, org.id(), "main", null))
        .isInstanceOf(AlreadyExistsException.class);
    Organization other = f.catalog.createOrganization(ADMIN, "Other");
    assertThat(f.catalog.createSite(ADMIN, other.id(), "Main", null)).isNotNull();
  }

  @Test
  void registerAssetPublishesAssetRegisteredAsTheUser() {
    Site site = siteOf(f.catalog.createOrganization(ADMIN, "Acme"));

    Asset asset =
        f.catalog.registerAsset(ADMIN, site.id(), "Cold room 1", "Vaccines", Criticality.CRITICAL);

    OutboundEvent event = f.events.only();
    assertThat(event.eventType()).isEqualTo("AssetRegistered");
    assertThat(event.routingKey()).isEqualTo("asset.asset-registered");
    assertThat(event.aggregateType()).isEqualTo("Asset");
    assertThat(event.aggregateId()).isEqualTo(asset.id().toString());
    assertThat(event.actor()).isEqualTo(EventActor.user("admin-1"));
    assertThat(event.occurredAt()).isEqualTo(ServiceFixture.NOW);
    EventContract.assertConforms(event);
  }

  @Test
  void updateAssetPublishesOnlyTheChangedFields() {
    Asset asset = f.anAsset();
    f.events.published.clear();

    Asset updated =
        f.catalog.updateAsset(ADMIN, asset.id(), 1, "Cold room 2", null, Criticality.CRITICAL);

    assertThat(updated.version()).isEqualTo(2);
    assertThat(updated.name()).isEqualTo("Cold room 2");
    OutboundEvent event = f.events.only();
    assertThat(event.eventType()).isEqualTo("AssetUpdated");
    assertThat(event.routingKey()).isEqualTo("asset.asset-updated");
    @SuppressWarnings("unchecked")
    Map<String, Object> previous = payloadMap(event, "previous");
    assertThat(previous)
        .containsOnlyKeys("name", "criticality")
        .containsEntry("criticality", "HIGH");
    assertThat(payloadMap(event, "current"))
        .containsEntry("name", "Cold room 2")
        .containsEntry("criticality", "CRITICAL");
    EventContract.assertConforms(event);
  }

  @Test
  void anUpdateThatChangesNothingWritesAndPublishesNothing() {
    Asset asset = f.anAsset();
    f.events.published.clear();

    Asset same = f.catalog.updateAsset(ADMIN, asset.id(), 1, "Cold room 1", null, Criticality.HIGH);

    assertThat(same.version()).isEqualTo(1);
    assertThat(f.events.published).isEmpty();
    assertThat(f.store.assets.get(asset.id()).version()).isEqualTo(1);
  }

  @Test
  void updateWithAnOutdatedVersionIsRejected() {
    Asset asset = f.anAsset();
    f.catalog.updateAsset(ADMIN, asset.id(), 1, "Renamed", null, null);

    assertThatThrownBy(() -> f.catalog.updateAsset(ADMIN, asset.id(), 1, "Again", null, null))
        .isInstanceOf(StaleVersionException.class);
    assertThat(f.store.assets.get(asset.id()).name()).isEqualTo("Renamed");
  }

  @Test
  void unknownAssetsAreNotFound() {
    assertThatThrownBy(() -> f.catalog.getAsset(ADMIN, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> f.catalog.updateAsset(ADMIN, UUID.randomUUID(), 1, "x", null, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void pagingUsesTheDefaultSizeAndRejectsOutOfRangeValues() {
    for (int i = 0; i < 25; i++) {
      f.catalog.createOrganization(ADMIN, "Org " + String.format("%02d", i));
    }

    var first = f.catalog.listOrganizations(ADMIN, 0, 0);
    assertThat(first.size()).isEqualTo(20);
    assertThat(first.items()).hasSize(20);
    assertThat(first.totalElements()).isEqualTo(25);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(f.catalog.listOrganizations(ADMIN, 1, 0).items()).hasSize(5);

    assertThatThrownBy(() -> f.catalog.listOrganizations(ADMIN, 0, 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> f.catalog.listOrganizations(ADMIN, -1, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> f.catalog.listOrganizations(ADMIN, 0, -5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Site siteOf(Organization organization) {
    return f.catalog.createSite(ADMIN, organization.id(), "Main", null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payloadMap(OutboundEvent event, String field) {
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    return mapper.convertValue(mapper.valueToTree(event.payload()).get(field), Map.class);
  }
}
