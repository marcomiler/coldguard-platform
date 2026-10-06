package com.coldguard.incident.identity.domain;

/** The five human actors of the platform; the names are the vocabulary of the JWT roles claim. */
public enum Role {
  OPERATIONS_SUPERVISOR,
  OPERATOR,
  MAINTENANCE_TECHNICIAN,
  AUDITOR,
  PLATFORM_ADMIN
}
