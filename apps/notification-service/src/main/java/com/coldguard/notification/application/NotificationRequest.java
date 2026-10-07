package com.coldguard.notification.application;

import java.util.List;
import java.util.UUID;

/** A request to notify, as Incident Service asks for it. Addresses live only here, never stored. */
public record NotificationRequest(
    UUID requestId,
    UUID incidentId,
    String type,
    String priority,
    String assetId,
    String sensorId,
    List<Recipient> recipients) {

  public record Recipient(String userId, String email) {}
}
