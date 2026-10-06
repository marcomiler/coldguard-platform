package com.coldguard.asset.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset position in a sensor's history: the {@code (occurredAt, id)} of the last entry of the
 * previous page. It is opaque to clients; a malformed one is an invalid argument.
 */
public record HistoryCursor(Instant occurredAt, UUID id) {

  public static HistoryCursor after(SensorHistoryEntry entry) {
    return new HistoryCursor(entry.occurredAt(), entry.id());
  }

  public String encode() {
    String raw = occurredAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Null or empty means "first page" and returns null. */
  public static HistoryCursor decode(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      return new HistoryCursor(
          Instant.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (IllegalArgumentException
        | StringIndexOutOfBoundsException
        | DateTimeParseException e) {
      throw new IllegalArgumentException("invalid cursor");
    }
  }
}
