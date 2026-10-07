package com.coldguard.notification.application;

import static com.coldguard.notification.application.NotificationFixtures.INCIDENT_ID;
import static com.coldguard.notification.application.NotificationFixtures.REQUEST_ID;
import static com.coldguard.notification.application.NotificationFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.notification.application.NotificationFixtures.CapturedEvents;
import com.coldguard.notification.application.NotificationFixtures.InMemoryLedger;
import com.coldguard.notification.application.NotificationFixtures.ScriptedSender;
import com.coldguard.notification.application.NotificationSender.SendResult;
import com.coldguard.notification.domain.DeliveryStatus;
import com.coldguard.notification.domain.NotificationStatus;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class HandleNotificationRequestedServiceTest {

  private int sentCount;
  private final java.util.List<com.coldguard.notification.domain.FailureCategory> failures =
      new java.util.ArrayList<>();
  private final InMemoryLedger repository = new InMemoryLedger();
  private final CapturedEvents events = new CapturedEvents();
  private final ScriptedSender sender = new ScriptedSender();
  private final NotificationLedger ledger =
      new NotificationLedger(
          repository,
          events,
          Clock.systemUTC(),
          new NotificationMetrics() {
            @Override
            public void sent() {
              sentCount++;
            }

            @Override
            public void failed(com.coldguard.notification.domain.FailureCategory category) {
              failures.add(category);
            }
          });
  private final HandleNotificationRequestedService service =
      new HandleNotificationRequestedService(ledger, NotificationFixtures.RENDERER, sender);

  @Test
  void sendsOneMessagePerRecipientAndMarksTheRequestSent() {
    service.handle(request("u1", "u2"));

    assertThat(sender.attempted).containsExactly("u1", "u2");
    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.SENT);
    assertThat(events.published).isEmpty();
  }

  @Test
  void aRepeatedRequestSendsNothing() {
    service.handle(request("u1"));
    sender.attempted.clear();

    service.handle(request("u1"));

    assertThat(sender.attempted).isEmpty();
  }

  @Test
  void aPermanentFailureIsRecordedAndPublishedWhileTheOthersContinue() {
    sender.answers.put("u1", new SendResult.PermanentFailure("rejected"));

    service.handle(request("u1", "u2"));

    assertThat(repository.delivery.get("u1")).isEqualTo(DeliveryStatus.FAILED);
    assertThat(repository.delivery.get("u2")).isEqualTo(DeliveryStatus.SENT);
    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.PARTIALLY_SENT);
    assertThat(events.published).hasSize(1);
    var event = events.published.get(0);
    assertThat(event.eventType()).isEqualTo("NotificationFailed");
    assertThat(event.routingKey()).isEqualTo("notification.notification-failed");
    assertThat(event.aggregateType()).isEqualTo("Incident");
    assertThat(event.aggregateId()).isEqualTo(INCIDENT_ID.toString());
    assertThat(event.payload().toString()).contains("PERMANENT");
  }

  @Test
  void allRecipientsRejectedLeavesTheRequestFailed() {
    sender.answers.put("u1", new SendResult.PermanentFailure("rejected"));

    service.handle(request("u1"));

    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.FAILED);
  }

  @Test
  void aTransientFailureThrowsSoTheBrokerRedeliversAndOnlyPendingRecipientsAreRetried() {
    sender.answers.put("u2", new SendResult.TransientFailure("timeout"));

    assertThatThrownBy(() -> service.handle(request("u1", "u2")))
        .isInstanceOf(TransientDeliveryException.class);
    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.PENDING);
    assertThat(repository.delivery.get("u1")).isEqualTo(DeliveryStatus.SENT);
    assertThat(repository.attempts.get("u2")).isEqualTo(1);

    sender.attempted.clear();
    sender.answers.clear();
    service.handle(request("u1", "u2"));

    assertThat(sender.attempted).containsExactly("u2");
    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.SENT);
    assertThat(repository.attempts.get("u2")).isEqualTo(2);
  }

  @Test
  void exhaustedRetriesFailThePendingRecipientsAndPublishTheFailure() {
    sender.answers.put("u2", new SendResult.TransientFailure("timeout"));
    assertThatThrownBy(() -> service.handle(request("u1", "u2")))
        .isInstanceOf(TransientDeliveryException.class);

    ledger.recordRetriesExhausted(REQUEST_ID);

    assertThat(repository.delivery.get("u2")).isEqualTo(DeliveryStatus.FAILED);
    assertThat(repository.status.get(REQUEST_ID)).isEqualTo(NotificationStatus.PARTIALLY_SENT);
    assertThat(events.published).hasSize(1);
    assertThat(events.published.get(0).payload().toString())
        .contains("RETRIES_EXHAUSTED", "attempts=1");
  }

  @Test
  void exhaustedRetriesOfAFinishedRequestPublishNothing() {
    service.handle(request("u1"));

    ledger.recordRetriesExhausted(REQUEST_ID);

    assertThat(events.published).isEmpty();
  }

  @Test
  void deliveriesAndFailuresAreCountedByCategory() {
    sender.answers.put("u1", new SendResult.PermanentFailure("rejected"));
    sender.answers.put("u3", new SendResult.TransientFailure("timeout"));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.handle(request("u1", "u2", "u3")))
        .isInstanceOf(TransientDeliveryException.class);

    ledger.recordRetriesExhausted(REQUEST_ID);

    assertThat(sentCount).isEqualTo(1);
    assertThat(failures)
        .containsExactly(
            com.coldguard.notification.domain.FailureCategory.PERMANENT,
            com.coldguard.notification.domain.FailureCategory.RETRIES_EXHAUSTED);
  }
}
