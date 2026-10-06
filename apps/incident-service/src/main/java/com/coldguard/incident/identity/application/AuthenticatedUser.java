package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId, String username, String displayName, Set<Role> roles) {}
