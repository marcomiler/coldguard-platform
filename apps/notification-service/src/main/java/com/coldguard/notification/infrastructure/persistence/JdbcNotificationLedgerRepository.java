package com.coldguard.notification.infrastructure.persistence;

import com.coldguard.notification.application.NotificationLedgerRepository;
import com.coldguard.notification.application.NotificationRequest;
import com.coldguard.notification.domain.DeliveryStatus;
import com.coldguard.notification.domain.FailureCategory;
import com.coldguard.notification.domain.NotificationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNotificationLedgerRepository implements NotificationLedgerRepository {

  private final JdbcClient jdbc;

  JdbcNotificationLedgerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(NotificationRequest request, Instant now) {
    UUID id = UUID.randomUUID();
    int inserted =
        jdbc.sql(
                """
                INSERT INTO notification
                  (id, notification_request_id, incident_id, notification_type, priority, status,
                   created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                ON CONFLICT (notification_request_id) DO NOTHING
                """)
            .params(
                id,
                request.requestId(),
                request.incidentId(),
                request.type(),
                request.priority(),
                Timestamp.from(now))
            .update();
    if (inserted == 0) {
      return false;
    }
    for (NotificationRequest.Recipient recipient : request.recipients()) {
      jdbc.sql(
              """
              INSERT INTO notification_delivery (id, notification_id, recipient_user_id, status)
              VALUES (?, ?, ?, 'PENDING') ON CONFLICT DO NOTHING
              """)
          .params(UUID.randomUUID(), id, recipient.userId())
          .update();
    }
    return true;
  }

  @Override
  public Optional<Notification> find(UUID requestId) {
    return jdbc.sql(
            "SELECT id, incident_id, status FROM notification WHERE notification_request_id = ?")
        .param(requestId)
        .query(
            (rs, n) ->
                new Notification(
                    rs.getObject("id", UUID.class),
                    rs.getObject("incident_id", UUID.class),
                    NotificationStatus.valueOf(rs.getString("status"))))
        .optional();
  }

  @Override
  public List<Delivery> deliveries(UUID requestId) {
    return jdbc.sql(
            """
            SELECT d.recipient_user_id, d.status, d.attempts
              FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
             WHERE n.notification_request_id = ?
            """)
        .param(requestId)
        .query(
            (rs, n) ->
                new Delivery(
                    rs.getString("recipient_user_id"),
                    DeliveryStatus.valueOf(rs.getString("status")),
                    rs.getInt("attempts")))
        .list();
  }

  @Override
  public void markSent(UUID requestId, String recipientUserId, Instant now) {
    jdbc.sql(
            """
            UPDATE notification_delivery SET status = 'SENT', attempts = attempts + 1,
                   sent_at = ?, last_error_category = NULL
             WHERE recipient_user_id = ? AND notification_id =
                   (SELECT id FROM notification WHERE notification_request_id = ?)
            """)
        .params(Timestamp.from(now), recipientUserId, requestId)
        .update();
  }

  @Override
  public void recordTransientAttempt(UUID requestId, String recipientUserId, String category) {
    jdbc.sql(
            """
            UPDATE notification_delivery SET attempts = attempts + 1, last_error_category = ?
             WHERE recipient_user_id = ? AND notification_id =
                   (SELECT id FROM notification WHERE notification_request_id = ?)
            """)
        .params(category, recipientUserId, requestId)
        .update();
  }

  @Override
  public void markFailed(UUID requestId, String recipientUserId, FailureCategory category) {
    jdbc.sql(
            """
            UPDATE notification_delivery SET status = 'FAILED', attempts = attempts + ?,
                   last_error_category = ?
             WHERE recipient_user_id = ? AND notification_id =
                   (SELECT id FROM notification WHERE notification_request_id = ?)
            """)
        .params(
            category == FailureCategory.PERMANENT ? 1 : 0,
            category.name(),
            recipientUserId,
            requestId)
        .update();
  }

  @Override
  public void updateStatus(UUID requestId, NotificationStatus status, Instant now) {
    jdbc.sql(
            """
            UPDATE notification SET status = ?, completed_at = ?
             WHERE notification_request_id = ?
            """)
        .params(status.name(), status.isFinal() ? Timestamp.from(now) : null, requestId)
        .update();
  }
}
