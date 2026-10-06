package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record SiteResponse(
    String id, String organizationId, String name, String address, Instant createdAt) {}
