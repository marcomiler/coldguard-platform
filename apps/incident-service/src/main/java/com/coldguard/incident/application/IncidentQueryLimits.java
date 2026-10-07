package com.coldguard.incident.application;

/** Paging bounds for incident listings. */
public record IncidentQueryLimits(int defaultSize, int maxSize) {}
