package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.SiteRepository;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSiteRepository implements SiteRepository {

  private static final String COLUMNS =
      "SELECT id, organization_id, name, address, created_at, version FROM site";

  private final JdbcClient jdbc;

  JdbcSiteRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Site site) {
    try {
      jdbc.sql(
              """
              INSERT INTO site (id, organization_id, name, address, created_at, version)
              VALUES (?, ?, ?, ?, ?, ?)
              """)
          .params(
              site.id(),
              site.organizationId(),
              site.name(),
              site.address(),
              JdbcSupport.timestamp(site.createdAt()),
              site.version())
          .update();
    } catch (DuplicateKeyException e) {
      throw new AlreadyExistsException(
          "SITE_NAME_DUPLICATED",
          "The organization already has a site named '" + site.name() + "'");
    }
  }

  @Override
  public Optional<Site> findById(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = ?").param(id).query(this::map).optional();
  }

  @Override
  public List<Site> findPage(UUID organizationId, int page, int size) {
    return jdbc.sql(
            COLUMNS
                + " WHERE (?::uuid IS NULL OR organization_id = ?::uuid)"
                + " ORDER BY lower(name), id LIMIT ? OFFSET ?")
        .params(organizationId, organizationId, size, JdbcSupport.offset(page, size))
        .query(this::map)
        .list();
  }

  @Override
  public long count(UUID organizationId) {
    return jdbc.sql(
            "SELECT count(*) FROM site WHERE (?::uuid IS NULL OR organization_id = ?::uuid)")
        .params(organizationId, organizationId)
        .query(Long.class)
        .single();
  }

  private Site map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Site(
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getString("name"),
        rs.getString("address"),
        JdbcSupport.instant(rs, "created_at"),
        rs.getLong("version"));
  }
}
