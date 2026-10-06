package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record OrganizationResponse(String id, String name, Instant createdAt) {}
