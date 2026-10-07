package com.coldguard.gateway.api.metrics;

import java.util.List;
import java.util.Map;

public record IncidentMetricsResponse(
    Map<String, Long> countByStatus,
    Map<String, Long> countByPriority,
    Double mttaSeconds,
    Double mttrSeconds,
    List<PriorityMetricsResponse> byPriority) {

  public record PriorityMetricsResponse(
      String priority,
      long total,
      long acknowledged,
      long acknowledgedOnTime,
      long closed,
      long closedOnTime,
      Double ackComplianceRatio,
      Double resolveComplianceRatio) {}
}
