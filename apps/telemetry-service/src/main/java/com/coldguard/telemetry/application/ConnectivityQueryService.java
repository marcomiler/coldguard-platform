package com.coldguard.telemetry.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.SensorCondition;
import java.util.List;

/** Reads back which sensors are reporting and which have stopped. */
public class ConnectivityQueryService {

  /** One zero-based page of conditions plus the total number of matches. */
  public record ConnectivityPage(
      List<SensorCondition> items, int page, int size, long totalElements) {

    public int totalPages() {
      return (int) ((totalElements + size - 1) / size);
    }
  }

  private final SensorConditionRepository conditions;
  private final int maxPageSize;
  private final int defaultPageSize;

  public ConnectivityQueryService(
      SensorConditionRepository conditions, int maxPageSize, int defaultPageSize) {
    this.conditions = conditions;
    this.maxPageSize = maxPageSize;
    this.defaultPageSize = defaultPageSize;
  }

  /** {@code requestedSize} 0 means "the default". */
  public ConnectivityPage list(Actor actor, boolean onlyLost, int page, int requestedSize) {
    if (actor == null
        || !(actor.hasRole(Role.PLATFORM_ADMIN) || actor.hasRole(Role.OPERATIONS_SUPERVISOR))) {
      throw new TelemetryAccessDeniedException(
          "PLATFORM_ADMIN or OPERATIONS_SUPERVISOR role required");
    }
    int size = requestedSize == 0 ? defaultPageSize : requestedSize;
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }
    if (size < 1 || size > maxPageSize) {
      throw new IllegalArgumentException("size must be between 1 and " + maxPageSize);
    }
    return new ConnectivityPage(
        conditions.findPage(onlyLost, page, size), page, size, conditions.count(onlyLost));
  }
}
