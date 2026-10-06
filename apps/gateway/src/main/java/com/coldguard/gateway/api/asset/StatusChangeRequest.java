package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
    @NotNull SensorStatus targetStatus, @NotBlank @Size(max = 500) String reason) {}
