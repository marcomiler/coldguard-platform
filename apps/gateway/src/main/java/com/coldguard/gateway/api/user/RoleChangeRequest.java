package com.coldguard.gateway.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleChangeRequest(@NotNull Role role, @NotBlank @Size(max = 500) String reason) {}
