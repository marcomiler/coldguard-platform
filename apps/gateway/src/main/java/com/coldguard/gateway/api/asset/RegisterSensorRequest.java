package com.coldguard.gateway.api.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The initial calibration and the profile are optional: a sensor may be completed later. */
public record RegisterSensorRequest(
    @NotBlank String assetId,
    @NotBlank @Size(max = 80) String serialNumber,
    @Size(max = 80) String model,
    @NotBlank @Size(max = 20) String measurementUnit,
    @Valid InitialCalibrationRequest initialCalibration,
    @Valid OperationalProfileRequest profile) {}
