package com.coldguard.telemetry.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads back the readings of a sensor over a bounded time range. */
public class ReadingQueryService {

  /** One page of readings, newest first; {@code nextCursor} is empty on the last one. */
  public record ReadingPage(List<StoredReading> readings, String nextCursor, boolean hasMore) {}

  private final ReadingRepository readings;
  private final Duration maxRange;
  private final int maxPageSize;
  private final int defaultPageSize;

  public ReadingQueryService(
      ReadingRepository readings, Duration maxRange, int maxPageSize, int defaultPageSize) {
    this.readings = readings;
    this.maxRange = maxRange;
    this.maxPageSize = maxPageSize;
    this.defaultPageSize = defaultPageSize;
  }

  /** {@code requestedSize} 0 means "the default". */
  public ReadingPage list(
      Actor actor, UUID sensorId, Instant from, Instant to, String cursor, int requestedSize) {
    if (actor == null
        || !(actor.hasRole(Role.PLATFORM_ADMIN) || actor.hasRole(Role.OPERATIONS_SUPERVISOR))) {
      throw new TelemetryAccessDeniedException(
          "PLATFORM_ADMIN or OPERATIONS_SUPERVISOR role required");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to are required");
    }
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("from must be before to");
    }
    if (Duration.between(from, to).compareTo(maxRange) > 0) {
      throw new RangeTooWideException("The range is at most " + maxRange);
    }
    int size = requestedSize == 0 ? defaultPageSize : requestedSize;
    if (size < 1 || size > maxPageSize) {
      throw new IllegalArgumentException("size must be between 1 and " + maxPageSize);
    }
    List<StoredReading> rows =
        readings.findPage(sensorId, from, to, ReadingCursor.decode(cursor), size + 1);
    boolean hasMore = rows.size() > size;
    List<StoredReading> page = hasMore ? rows.subList(0, size) : rows;
    String next = hasMore ? ReadingCursor.after(page.get(page.size() - 1)).encode() : "";
    return new ReadingPage(List.copyOf(page), next, hasMore);
  }
}
