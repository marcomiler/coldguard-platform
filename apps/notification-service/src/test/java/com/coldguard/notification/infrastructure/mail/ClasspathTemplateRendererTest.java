package com.coldguard.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.notification.application.NotificationContent;
import com.coldguard.notification.application.NotificationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClasspathTemplateRendererTest {

  private final ClasspathTemplateRenderer renderer = new ClasspathTemplateRenderer();

  private static NotificationRequest request(String type) {
    return new NotificationRequest(
        UUID.randomUUID(),
        UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
        type,
        "P1",
        "asset-9",
        "sensor-9",
        List.of(new NotificationRequest.Recipient("u1", "u1@coldguard.test")));
  }

  @Test
  void fillsTheCreatedTemplateWithoutLeavingPlaceholders() {
    NotificationContent content = renderer.render(request("INCIDENT_CREATED"));

    assertThat(content.subject()).contains("P1").contains("creado");
    assertThat(content.body())
        .contains("00000000-0000-0000-0000-0000000000b1", "asset-9", "sensor-9", "P1")
        .doesNotContain("{{");
  }

  @Test
  void escalatedHasItsOwnTemplateAndNeverIncludesTheAddress() {
    NotificationContent content = renderer.render(request("INCIDENT_ESCALATED"));

    assertThat(content.subject()).contains("escalado");
    assertThat(content.body()).contains("escalado").doesNotContain("coldguard.test");
  }

  @Test
  void anUnknownTypeIsRejected() {
    assertThatThrownBy(() -> renderer.render(request("SOMETHING_ELSE")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
