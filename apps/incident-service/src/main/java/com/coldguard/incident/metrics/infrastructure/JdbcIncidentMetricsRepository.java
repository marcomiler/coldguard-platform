package com.coldguard.incident.metrics.infrastructure;

import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.metrics.application.IncidentMetricsRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregates in SQL, without loading incidents; uses the index on {@code created_at}. */
@Repository
class JdbcIncidentMetricsRepository implements IncidentMetricsRepository {

  private static final String AGGREGATE =
      """
      SELECT priority, status,
             count(*) AS total,
             count(*) FILTER (WHERE acknowledged_at IS NOT NULL) AS acknowledged,
             count(*) FILTER (WHERE acknowledged_at IS NOT NULL AND ack_due_at IS NOT NULL
                                AND acknowledged_at <= ack_due_at) AS acknowledged_on_time,
             count(*) FILTER (WHERE acknowledged_at IS NOT NULL AND ack_due_at IS NOT NULL)
                                                                      AS ack_evaluable,
             coalesce(sum(extract(epoch FROM acknowledged_at - created_at))
                      FILTER (WHERE acknowledged_at IS NOT NULL), 0) AS ack_seconds,
             count(*) FILTER (WHERE closed_at IS NOT NULL) AS closed,
             count(*) FILTER (WHERE closed_at IS NOT NULL AND resolve_due_at IS NOT NULL
                                AND closed_at <= resolve_due_at) AS closed_on_time,
             count(*) FILTER (WHERE closed_at IS NOT NULL AND resolve_due_at IS NOT NULL)
                                                                      AS close_evaluable,
             coalesce(sum(extract(epoch FROM closed_at - created_at))
                      FILTER (WHERE closed_at IS NOT NULL), 0) AS close_seconds
        FROM incident.incident
       WHERE created_at >= :from AND created_at < :to
       GROUP BY priority, status
      """;

  private final JdbcClient jdbc;

  JdbcIncidentMetricsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Group> aggregate(Instant from, Instant to) {
    return jdbc.sql(AGGREGATE)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query(
            (rs, n) ->
                new Group(
                    Priority.valueOf(rs.getString("priority")),
                    IncidentStatus.valueOf(rs.getString("status")),
                    rs.getLong("total"),
                    rs.getLong("acknowledged"),
                    rs.getLong("acknowledged_on_time"),
                    rs.getLong("ack_evaluable"),
                    rs.getDouble("ack_seconds"),
                    rs.getLong("closed"),
                    rs.getLong("closed_on_time"),
                    rs.getLong("close_evaluable"),
                    rs.getDouble("close_seconds")))
        .list();
  }
}
