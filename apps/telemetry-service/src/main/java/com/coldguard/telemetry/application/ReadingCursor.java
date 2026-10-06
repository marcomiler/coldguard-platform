package com.coldguard.telemetry.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Keyset position in a sensor's readings; opaque to clients, and a malformed one is invalid. */
public record ReadingCursor(Instant recordedAt, UUID id) {

  public static ReadingCursor after(StoredReading reading) {
    return new ReadingCursor(reading.recordedAt(), reading.id());
  }

  public String encode() {
    String raw = recordedAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Null or empty means "first page" and returns null. */
  public static ReadingCursor decode(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      return new ReadingCursor(
          Instant.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (IllegalArgumentException
        | StringIndexOutOfBoundsException
        | DateTimeParseException e) {
      throw new IllegalArgumentException("invalid cursor");
    }
  }
}
