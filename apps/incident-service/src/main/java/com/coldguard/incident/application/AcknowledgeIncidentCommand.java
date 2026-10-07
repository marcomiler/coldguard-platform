package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;

public record AcknowledgeIncidentCommand(String incidentId, Actor actor) {}
