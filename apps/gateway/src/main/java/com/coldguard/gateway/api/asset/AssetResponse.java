package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record AssetResponse(
    String id,
    String siteId,
    String name,
    String description,
    Criticality criticality,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
