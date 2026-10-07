package com.coldguard.incident.auditlog.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.auditlog.application.AuditRecordRepository.Cursor;
import com.coldguard.incident.auditlog.application.AuditRecordRepository.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only query of the audit trail (auditors only), keyset-paged by (occurredAt, id). */
@Service
public class ListAuditRecordsService {

  public record Query(
      String entityType,
      String entityId,
      String actorId,
      String action,
      Instant from,
      Instant to,
      String cursor,
      int size) {}

  public record Page(List<AuditRecord> records, String nextCursor, boolean hasMore) {}

  private final AuditRecordRepository repository;
  private final AuditQueryLimits limits;

  ListAuditRecordsService(AuditRecordRepository repository, AuditQueryLimits limits) {
    this.repository = repository;
    this.limits = limits;
  }

  /**
   * @throws IllegalArgumentException if the range is missing, inverted, wider than allowed, or the
   *     page size or cursor is invalid
   */
  @Transactional(readOnly = true)
  public Page list(Actor actor, Query query) {
    if (actor == null || !actor.hasRole(Role.AUDITOR)) {
      throw new AuditAccessDeniedException(actor);
    }
    if (query.from() == null || query.to() == null) {
      throw new IllegalArgumentException("from and to are required");
    }
    if (!query.from().isBefore(query.to())) {
      throw new IllegalArgumentException("from must be before to");
    }
    if (Duration.between(query.from(), query.to()).compareTo(limits.maxRange()) > 0) {
      throw new IllegalArgumentException("date range must not exceed " + limits.maxRange());
    }
    if (query.size() < 0 || query.size() > limits.maxPageSize()) {
      throw new IllegalArgumentException("page size must be between 0 and " + limits.maxPageSize());
    }
    int size = query.size() == 0 ? limits.defaultPageSize() : query.size();
    List<AuditRecord> fetched =
        repository.search(
            new Filter(
                blankToNull(query.entityType()),
                blankToNull(query.entityId()),
                blankToNull(query.actorId()),
                blankToNull(query.action()),
                query.from(),
                query.to()),
            decode(query.cursor()),
            size + 1);
    boolean hasMore = fetched.size() > size;
    List<AuditRecord> page = hasMore ? fetched.subList(0, size) : fetched;
    String next = hasMore ? encode(page.get(page.size() - 1)) : "";
    return new Page(List.copyOf(page), next, hasMore);
  }

  private static String encode(AuditRecord last) {
    String raw = last.occurredAt() + "|" + last.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", 2);
      return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("cursor is not valid");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
