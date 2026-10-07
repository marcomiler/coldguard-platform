package com.coldguard.incident.application;

import com.coldguard.incident.domain.IncidentEvent;

/**
 * Port for what happens once an incident transition has committed: business-event logs and metrics.
 * Implementations act only after the surrounding transaction commits, so a rolled-back change
 * leaves no trace.
 */
public interface IncidentObserver {

  void transitioned(IncidentEvent event);
}
