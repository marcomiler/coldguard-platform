package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;

/** {@code actor} is null when the call carried no trusted identity. */
public record CreateIncidentCommand(OpenIncidentCommand anomaly, Actor actor) {}
