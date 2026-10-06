package com.coldguard.gateway.api.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TestReadingsRequest(@NotEmpty @Size(max = 500) List<@Valid TestReading> readings) {}
