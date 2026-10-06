package com.coldguard.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record Organization(UUID id, String name, Instant createdAt, long version) {

  public Organization {
    name = Text.required(name, "name", 120);
  }

  public static Organization create(String name, Instant now) {
    return new Organization(UUID.randomUUID(), name, now, 1);
  }
}
