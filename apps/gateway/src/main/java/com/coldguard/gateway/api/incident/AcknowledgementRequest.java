package com.coldguard.gateway.api.incident;

import jakarta.validation.constraints.Size;

public record AcknowledgementRequest(@Size(max = 500) String note) {}
