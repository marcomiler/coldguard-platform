package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.EvaluationContextRepository;
import com.coldguard.asset.application.SensorEvaluationContext;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.SensorStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEvaluationContextRepository implements EvaluationContextRepository {

  private static final String SELECT =
      "SELECT s.id AS sensor_id, s.asset_id, a.criticality, s.status, "
          + ProfileRows.columns("p", "p_")
          + """

            FROM sensor s
            JOIN asset a ON a.id = s.asset_id
            LEFT JOIN operational_profile p ON p.sensor_id = s.id
           WHERE s.id = ANY (?)
           ORDER BY s.id
          """;

  private final JdbcClient jdbc;

  JdbcEvaluationContextRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<SensorEvaluationContext> findByIds(Collection<UUID> sensorIds) {
    return jdbc.sql(SELECT)
        .param(sensorIds.toArray(UUID[]::new))
        .query(
            (rs, row) ->
                new SensorEvaluationContext(
                    rs.getObject("sensor_id", UUID.class),
                    rs.getObject("asset_id", UUID.class),
                    Criticality.valueOf(rs.getString("criticality")),
                    SensorStatus.valueOf(rs.getString("status")),
                    ProfileRows.read(rs, "p_")))
        .list();
  }
}
