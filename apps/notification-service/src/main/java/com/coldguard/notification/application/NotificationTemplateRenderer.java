package com.coldguard.notification.application;

public interface NotificationTemplateRenderer {

  /**
   * @throws IllegalArgumentException if the notification type has no template
   */
  NotificationContent render(NotificationRequest request);
}
