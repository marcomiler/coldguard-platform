package com.coldguard.asset.application;

import java.time.Instant;
import java.util.UUID;

/** A sensor whose calibration has expired; also the keyset position while walking them. */
public record DueSensor(UUID sensorId, Instant validUntil) {}
