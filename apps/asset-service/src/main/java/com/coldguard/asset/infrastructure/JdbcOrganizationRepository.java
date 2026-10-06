package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.OrganizationRepository;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrganizationRepository implements OrganizationRepository {

  private static final String COLUMNS = "SELECT id, name, created_at, version FROM organization";

  private final JdbcClient jdbc;

  JdbcOrganizationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Organization organization) {
    try {
      jdbc.sql("INSERT INTO organization (id, name, created_at, version) VALUES (?, ?, ?, ?)")
          .params(
              organization.id(),
              organization.name(),
              JdbcSupport.timestamp(organization.createdAt()),
              organization.version())
          .update();
    } catch (DuplicateKeyException e) {
      throw new AlreadyExistsException(
          "ORGANIZATION_NAME_DUPLICATED",
          "An organization named '" + organization.name() + "' already exists");
    }
  }

  @Override
  public Optional<Organization> findById(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = ?").param(id).query(this::map).optional();
  }

  @Override
  public List<Organization> findPage(int page, int size) {
    return jdbc.sql(COLUMNS + " ORDER BY lower(name), id LIMIT ? OFFSET ?")
        .params(size, JdbcSupport.offset(page, size))
        .query(this::map)
        .list();
  }

  @Override
  public long count() {
    return jdbc.sql("SELECT count(*) FROM organization").query(Long.class).single();
  }

  private Organization map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Organization(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        JdbcSupport.instant(rs, "created_at"),
        rs.getLong("version"));
  }
}
