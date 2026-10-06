package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;

/** {@code actor} is null when the call carried no trusted identity. */
public record CloseIncidentCommand(
    String incidentId, String cause, String resolutionComment, Actor actor) {}
