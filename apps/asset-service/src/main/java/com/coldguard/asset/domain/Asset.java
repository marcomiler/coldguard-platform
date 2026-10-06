package com.coldguard.asset.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A cold unit. Its criticality is mandatory: it is what prioritizes the incidents it causes. */
public record Asset(
    UUID id,
    UUID siteId,
    String name,
    String description,
    Criticality criticality,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public Asset {
    name = Text.required(name, "name", 120);
    description = Text.optional(description, "description", 500);
    if (criticality == null) {
      throw new IllegalArgumentException("criticality is required");
    }
  }

  public static Asset register(
      UUID siteId, String name, String description, Criticality criticality, Instant now) {
    return new Asset(UUID.randomUUID(), siteId, name, description, criticality, now, now, 1);
  }

  /** A null argument leaves the field as it is; a blank description clears it. */
  public Asset update(
      String newName, String newDescription, Criticality newCriticality, Instant now) {
    return new Asset(
        id,
        siteId,
        newName == null ? name : newName,
        newDescription == null ? description : newDescription,
        newCriticality == null ? criticality : newCriticality,
        createdAt,
        now,
        version);
  }

  /** Names of the fields whose value differs from {@code before}. */
  public List<String> changedFieldsFrom(Asset before) {
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(name, before.name)) {
      changed.add("name");
    }
    if (!Objects.equals(description, before.description)) {
      changed.add("description");
    }
    if (criticality != before.criticality) {
      changed.add("criticality");
    }
    return changed;
  }

  public Asset withVersion(long newVersion) {
    return new Asset(id, siteId, name, description, criticality, createdAt, updatedAt, newVersion);
  }
}
