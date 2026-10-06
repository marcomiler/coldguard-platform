package com.coldguard.asset.domain;

/** The sensor cannot move to that asset: it is not in maintenance, or it is already there. */
public class ReassignmentNotAllowedException extends BusinessRuleViolationException {

  public ReassignmentNotAllowedException(String message) {
    super("REASSIGNMENT_NOT_ALLOWED", message);
  }
}
