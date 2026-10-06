package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.AssetRepository;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.StaleVersionException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssetRepository implements AssetRepository {

  private static final String COLUMNS =
      """
      SELECT id, site_id, name, description, criticality, created_at, updated_at, version
        FROM asset
      """;

  private final JdbcClient jdbc;

  JdbcAssetRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Asset asset) {
    jdbc.sql(
            """
            INSERT INTO asset
              (id, site_id, name, description, criticality, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            asset.id(),
            asset.siteId(),
            asset.name(),
            asset.description(),
            asset.criticality().name(),
            JdbcSupport.timestamp(asset.createdAt()),
            JdbcSupport.timestamp(asset.updatedAt()),
            asset.version())
        .update();
  }

  @Override
  public Optional<Asset> findById(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = ?").param(id).query(this::map).optional();
  }

  @Override
  public Asset update(Asset asset) {
    int updated =
        jdbc.sql(
                """
                UPDATE asset
                   SET name = ?, description = ?, criticality = ?, updated_at = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """)
            .params(
                asset.name(),
                asset.description(),
                asset.criticality().name(),
                JdbcSupport.timestamp(asset.updatedAt()),
                asset.id(),
                asset.version())
            .update();
    if (updated == 0) {
      throw new StaleVersionException("Asset", asset.id());
    }
    return asset.withVersion(asset.version() + 1);
  }

  @Override
  public List<Asset> findPage(UUID siteId, int page, int size) {
    return jdbc.sql(
            COLUMNS
                + " WHERE (?::uuid IS NULL OR site_id = ?::uuid)"
                + " ORDER BY lower(name), id LIMIT ? OFFSET ?")
        .params(siteId, siteId, size, JdbcSupport.offset(page, size))
        .query(this::map)
        .list();
  }

  @Override
  public long count(UUID siteId) {
    return jdbc.sql("SELECT count(*) FROM asset WHERE (?::uuid IS NULL OR site_id = ?::uuid)")
        .params(siteId, siteId)
        .query(Long.class)
        .single();
  }

  private Asset map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Asset(
        rs.getObject("id", UUID.class),
        rs.getObject("site_id", UUID.class),
        rs.getString("name"),
        rs.getString("description"),
        Criticality.valueOf(rs.getString("criticality")),
        JdbcSupport.instant(rs, "created_at"),
        JdbcSupport.instant(rs, "updated_at"),
        rs.getLong("version"));
  }
}
