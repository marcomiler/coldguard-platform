package com.coldguard.incident.domain;

/** The new state of an incident and the event that explains how it got there. */
public record Transition(Incident incident, IncidentEvent event) {}
