package com.coldguard.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record Site(
    UUID id, UUID organizationId, String name, String address, Instant createdAt, long version) {

  public Site {
    name = Text.required(name, "name", 120);
    address = Text.optional(address, "address", 250);
  }

  public static Site create(UUID organizationId, String name, String address, Instant now) {
    return new Site(UUID.randomUUID(), organizationId, name, address, now, 1);
  }
}
