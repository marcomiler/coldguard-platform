package com.coldguard.incident.identity.infrastructure;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.identity.application.UserAccountRepository;
import com.coldguard.incident.identity.application.UserAlreadyExistsException;
import com.coldguard.incident.identity.domain.UserAccount;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

  private static final String COLUMNS =
      """
      SELECT id, username, email, display_name, password_hash, enabled, failed_attempts,
             locked_until, created_at, updated_at
        FROM identity.user_account
      """;

  private static final String SELECT = COLUMNS + " WHERE lower(username) = ?";

  private static final String SELECT_BY_ID = COLUMNS + " WHERE id = ?";

  private final JdbcClient jdbc;

  public JdbcUserAccountRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UserAccount> findByUsername(String normalizedUsername) {
    return find(SELECT, normalizedUsername);
  }

  @Override
  public Optional<UserAccount> findByUsernameForUpdate(String normalizedUsername) {
    return find(SELECT + " FOR UPDATE", normalizedUsername);
  }

  private Optional<UserAccount> find(String sql, Object key) {
    return jdbc.sql(sql).param(key).query(this::map).optional();
  }

  private UserAccount map(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
    UUID id = rs.getObject("id", UUID.class);
    OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
    return new UserAccount(
        id,
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("display_name"),
        rs.getString("password_hash"),
        rs.getBoolean("enabled"),
        rs.getInt("failed_attempts"),
        lockedUntil == null ? null : lockedUntil.toInstant(),
        rolesOf(id),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return find(SELECT_BY_ID, id);
  }

  @Override
  public Optional<UserAccount> findByIdForUpdate(UUID id) {
    return find(SELECT_BY_ID + " FOR UPDATE", id);
  }

  @Override
  public List<UserAccount> findPage(int page, int size) {
    return jdbc.sql(COLUMNS + " ORDER BY lower(username) LIMIT ? OFFSET ?")
        .params(size, (long) page * size)
        .query(this::map)
        .list();
  }

  @Override
  public long count() {
    return jdbc.sql("SELECT count(*) FROM identity.user_account").query(Long.class).single();
  }

  @Override
  public List<UserAccount> findEnabledByRole(Role role) {
    return jdbc.sql(
            COLUMNS
                + """
                   WHERE enabled
                     AND id IN (SELECT user_id FROM identity.user_role WHERE role = ?)
                   ORDER BY lower(username)
                  """)
        .param(role.name())
        .query(this::map)
        .list();
  }

  @Override
  public long countOtherEnabledWithRoleForUpdate(Role role, UUID excludedUserId) {
    // FOR UPDATE cannot be combined with count(*), so the rows are locked and counted by hand.
    return jdbc.sql(
            """
            SELECT a.id
              FROM identity.user_account a
             WHERE a.enabled
               AND a.id <> ?
               AND a.id IN (SELECT user_id FROM identity.user_role WHERE role = ?)
             ORDER BY a.id
               FOR UPDATE OF a
            """)
        .params(excludedUserId, role.name())
        .query((rs, n) -> rs.getObject("id", UUID.class))
        .list()
        .size();
  }

  private Set<Role> rolesOf(UUID userId) {
    List<Role> roles =
        jdbc.sql("SELECT role FROM identity.user_role WHERE user_id = ?")
            .param(userId)
            .query((rs, n) -> Role.valueOf(rs.getString("role")))
            .list();
    return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles);
  }

  @Override
  public void insert(UserAccount user, String assignedBy) {
    try {
      jdbc.sql(
              """
              INSERT INTO identity.user_account
                (id, username, email, display_name, password_hash, enabled, failed_attempts,
                 locked_until, created_at, updated_at, version)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
              """)
          .params(
              user.id(),
              user.username(),
              user.email(),
              user.displayName(),
              user.passwordHash(),
              user.enabled(),
              user.failedAttempts(),
              timestamp(user.lockedUntil()),
              Timestamp.from(user.createdAt()),
              Timestamp.from(user.updatedAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw new UserAlreadyExistsException("Username or email already in use", e);
    }
    for (Role role : user.roles()) {
      jdbc.sql(
              """
              INSERT INTO identity.user_role (user_id, role, assigned_at, assigned_by)
              VALUES (?, ?, ?, ?)
              """)
          .params(user.id(), role.name(), Timestamp.from(user.createdAt()), assignedBy)
          .update();
    }
  }

  @Override
  public void updateLoginState(UserAccount user) {
    jdbc.sql(
            """
            UPDATE identity.user_account
               SET failed_attempts = ?, locked_until = ?, updated_at = ?, version = version + 1
             WHERE id = ?
            """)
        .params(
            user.failedAttempts(),
            timestamp(user.lockedUntil()),
            Timestamp.from(user.updatedAt()),
            user.id())
        .update();
  }

  @Override
  public void updateAccess(UserAccount user, String assignedBy) {
    jdbc.sql(
            """
            UPDATE identity.user_account
               SET enabled = ?, updated_at = ?, version = version + 1
             WHERE id = ?
            """)
        .params(user.enabled(), Timestamp.from(user.updatedAt()), user.id())
        .update();
    Set<Role> stored = rolesOf(user.id());
    for (Role role : stored) {
      if (!user.roles().contains(role)) {
        jdbc.sql("DELETE FROM identity.user_role WHERE user_id = ? AND role = ?")
            .params(user.id(), role.name())
            .update();
      }
    }
    for (Role role : user.roles()) {
      if (!stored.contains(role)) {
        jdbc.sql(
                """
                INSERT INTO identity.user_role (user_id, role, assigned_at, assigned_by)
                VALUES (?, ?, ?, ?)
                """)
            .params(user.id(), role.name(), Timestamp.from(user.updatedAt()), assignedBy)
            .update();
      }
    }
  }

  private static Timestamp timestamp(java.time.Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
