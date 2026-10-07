package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;

public record EscalateIncidentCommand(String incidentId, String reason, Actor actor) {}
