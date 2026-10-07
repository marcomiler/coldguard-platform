package com.coldguard.incident.auditlog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.auditlog.application.AuditRecordRepository.Cursor;
import com.coldguard.incident.auditlog.application.AuditRecordRepository.Filter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListAuditRecordsServiceTest {

  private static final Actor AUDITOR = new Actor("aud-1", Set.of(Role.AUDITOR));
  private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

  private final List<AuditRecord> stored = new ArrayList<>();
  private Cursor lastCursor;
  private int lastLimit;

  private final AuditRecordRepository repository =
      new AuditRecordRepository() {
        @Override
        public boolean append(AuditRecord record, Instant recordedAt, UUID sourceEventId) {
          return stored.add(record);
        }

        @Override
        public List<AuditRecord> search(Filter filter, Cursor after, int limit) {
          lastCursor = after;
          lastLimit = limit;
          return stored.stream().limit(limit).toList();
        }
      };

  private final ListAuditRecordsService service =
      new ListAuditRecordsService(repository, new AuditQueryLimits(Duration.ofDays(31), 50, 200));

  private static ListAuditRecordsService.Query query(
      Instant from, Instant to, String cursor, int size) {
    return new ListAuditRecordsService.Query(null, null, null, null, from, to, cursor, size);
  }

  private static AuditRecord record(int n) {
    return new AuditRecord(
        UUID.randomUUID(),
        FROM.plusSeconds(n),
        "incident-service",
        "Incident",
        "i",
        "CREATED",
        "USER",
        "u",
        null,
        null,
        null,
        null);
  }

  @Test
  void onlyAuditorsMayRead() {
    for (Actor actor : new Actor[] {null, new Actor("s", Set.of(Role.OPERATIONS_SUPERVISOR))}) {
      assertThatThrownBy(() -> service.list(actor, query(FROM, TO, null, 0)))
          .isInstanceOf(AuditAccessDeniedException.class);
    }
  }

  @Test
  void rangeIsRequiredOrderedAndBounded() {
    assertThatThrownBy(() -> service.list(AUDITOR, query(null, TO, null, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(AUDITOR, query(TO, FROM, null, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.list(AUDITOR, query(FROM, FROM.plus(Duration.ofDays(32)), null, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("range");
  }

  @Test
  void pageSizeIsBounded() {
    assertThatThrownBy(() -> service.list(AUDITOR, query(FROM, TO, null, 201)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pagesWithAnOpaqueCursorThatResumesAfterTheLastRecord() {
    for (int n = 0; n < 5; n++) {
      stored.add(record(n));
    }

    ListAuditRecordsService.Page first = service.list(AUDITOR, query(FROM, TO, null, 2));

    assertThat(first.records()).hasSize(2);
    assertThat(first.hasMore()).isTrue();
    assertThat(lastLimit).isEqualTo(3);

    service.list(AUDITOR, query(FROM, TO, first.nextCursor(), 2));
    assertThat(lastCursor.id()).isEqualTo(first.records().get(1).id());
    assertThat(lastCursor.occurredAt()).isEqualTo(first.records().get(1).occurredAt());
  }

  @Test
  void lastPageHasNoCursor() {
    stored.add(record(0));

    ListAuditRecordsService.Page page = service.list(AUDITOR, query(FROM, TO, null, 10));

    assertThat(page.hasMore()).isFalse();
    assertThat(page.nextCursor()).isEmpty();
  }

  @Test
  void garbageCursorIsInvalidArgument() {
    assertThatThrownBy(() -> service.list(AUDITOR, query(FROM, TO, "not-a-cursor!", 10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cursor");
  }
}
