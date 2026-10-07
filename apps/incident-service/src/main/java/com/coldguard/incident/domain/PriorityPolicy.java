package com.coldguard.incident.domain;

/**
 * Pure, stateless derivation of impact, urgency and priority for a new incident.
 *
 * <p>{@link #impactFrom(Criticality)} and {@link #urgencyFrom(Magnitude, boolean)} are a
 * simplified, deterministic placeholder mapping for this slice (asset criticality maps directly to
 * impact; anomaly magnitude maps directly to urgency, escalated one level when persistent). They
 * are not a confirmed business formula and should be revisited once real classification logic is
 * designed. {@link #priorityFrom(Impact, Urgency)} implements the fixed impact/urgency priority
 * matrix.
 */
public final class PriorityPolicy {

  private PriorityPolicy() {}

  public static Impact impactFrom(Criticality criticality) {
    return switch (criticality) {
      case LOW -> Impact.LOW;
      case MEDIUM -> Impact.MEDIUM;
      case HIGH -> Impact.HIGH;
      case CRITICAL -> Impact.CRITICAL;
    };
  }

  public static Urgency urgencyFrom(Magnitude magnitude, boolean persistent) {
    Urgency base =
        switch (magnitude) {
          case LOW -> Urgency.LOW;
          case MEDIUM -> Urgency.MEDIUM;
          case HIGH -> Urgency.HIGH;
          case CRITICAL -> Urgency.IMMEDIATE;
        };
    if (!persistent) {
      return base;
    }
    return switch (base) {
      case LOW -> Urgency.MEDIUM;
      case MEDIUM -> Urgency.HIGH;
      case HIGH, IMMEDIATE -> Urgency.IMMEDIATE;
    };
  }

  public static Priority priorityFrom(Impact impact, Urgency urgency) {
    return switch (impact) {
      case CRITICAL ->
          switch (urgency) {
            case IMMEDIATE, HIGH -> Priority.P1;
            case MEDIUM, LOW -> Priority.P2;
          };
      case HIGH ->
          switch (urgency) {
            case IMMEDIATE -> Priority.P1;
            case HIGH, MEDIUM -> Priority.P2;
            case LOW -> Priority.P3;
          };
      case MEDIUM ->
          switch (urgency) {
            case IMMEDIATE -> Priority.P2;
            case HIGH, MEDIUM -> Priority.P3;
            case LOW -> Priority.P4;
          };
      case LOW ->
          switch (urgency) {
            case IMMEDIATE -> Priority.P3;
            case HIGH, MEDIUM -> Priority.P4;
            case LOW -> Priority.P4;
          };
    };
  }
}
