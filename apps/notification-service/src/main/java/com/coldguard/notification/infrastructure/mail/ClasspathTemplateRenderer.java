package com.coldguard.notification.infrastructure.mail;

import com.coldguard.notification.application.NotificationContent;
import com.coldguard.notification.application.NotificationRequest;
import com.coldguard.notification.application.NotificationTemplateRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Plain-text templates in {@code templates/} with {@code {{name}}} substitution; no template engine
 * is needed. Only the incident, asset, sensor and priority are inserted, nothing sensitive.
 */
@Component
class ClasspathTemplateRenderer implements NotificationTemplateRenderer {

  private static final Map<String, Template> TEMPLATES =
      Map.of(
          "INCIDENT_CREATED",
          new Template("incident-created.txt", "ColdGuard: incidente {{priority}} creado"),
          "INCIDENT_ESCALATED",
          new Template("incident-escalated.txt", "ColdGuard: incidente {{priority}} escalado"));

  private record Template(String file, String subject) {}

  @Override
  public NotificationContent render(NotificationRequest request) {
    Template template = TEMPLATES.get(request.type());
    if (template == null) {
      throw new IllegalArgumentException("No template for notification type " + request.type());
    }
    return new NotificationContent(
        fill(template.subject(), request), fill(load(template.file()), request));
  }

  private static String fill(String text, NotificationRequest request) {
    return text.replace("{{priority}}", request.priority() == null ? "-" : request.priority())
        .replace("{{incidentId}}", request.incidentId().toString())
        .replace("{{assetId}}", request.assetId())
        .replace("{{sensorId}}", request.sensorId());
  }

  private static String load(String file) {
    try (InputStream in =
        ClasspathTemplateRenderer.class.getResourceAsStream("/templates/" + file)) {
      if (in == null) {
        throw new IllegalStateException("Missing template " + file);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read template " + file, e);
    }
  }
}
