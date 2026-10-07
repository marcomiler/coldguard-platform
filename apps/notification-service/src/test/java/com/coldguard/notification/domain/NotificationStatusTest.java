package com.coldguard.notification.domain;

import static com.coldguard.notification.domain.DeliveryStatus.FAILED;
import static com.coldguard.notification.domain.DeliveryStatus.PENDING;
import static com.coldguard.notification.domain.DeliveryStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationStatusTest {

  @Test
  void derivesTheOverallStateFromTheDeliveries() {
    assertThat(NotificationStatus.of(List.of(SENT, SENT))).isEqualTo(NotificationStatus.SENT);
    assertThat(NotificationStatus.of(List.of(SENT, PENDING))).isEqualTo(NotificationStatus.PENDING);
    assertThat(NotificationStatus.of(List.of(FAILED, PENDING)))
        .isEqualTo(NotificationStatus.PENDING);
    assertThat(NotificationStatus.of(List.of(SENT, FAILED)))
        .isEqualTo(NotificationStatus.PARTIALLY_SENT);
    assertThat(NotificationStatus.of(List.of(FAILED, FAILED))).isEqualTo(NotificationStatus.FAILED);
  }

  @Test
  void onlyPendingIsNotFinal() {
    assertThat(NotificationStatus.PENDING.isFinal()).isFalse();
    assertThat(NotificationStatus.SENT.isFinal()).isTrue();
    assertThat(NotificationStatus.PARTIALLY_SENT.isFinal()).isTrue();
    assertThat(NotificationStatus.FAILED.isFinal()).isTrue();
  }
}
