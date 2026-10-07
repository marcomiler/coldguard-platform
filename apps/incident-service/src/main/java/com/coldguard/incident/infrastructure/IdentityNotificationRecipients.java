package com.coldguard.incident.infrastructure;

import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.NotificationRecipients;
import com.coldguard.incident.identity.application.ListUserContactsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Resolves recipients from Identity & Access in the same process. */
@Component
class IdentityNotificationRecipients implements NotificationRecipients {

  private static final Logger log = LoggerFactory.getLogger(IdentityNotificationRecipients.class);

  private final ListUserContactsService contacts;
  private final MeterRegistry registry;

  IdentityNotificationRecipients(ListUserContactsService contacts, MeterRegistry registry) {
    this.contacts = contacts;
    this.registry = registry;
  }

  @Override
  public List<Recipient> enabledWithRole(Role role) {
    List<Recipient> found =
        contacts.enabledWithRole(role).stream()
            .map(contact -> new Recipient(contact.userId(), contact.email()))
            .toList();
    if (found.isEmpty()) {
      // No notification can be requested; the incident itself is unaffected. No personal data here.
      log.warn("No enabled recipients for role {}; notification not requested", role);
      Counter.builder("coldguard.notification.recipients.missing")
          .tag("role", role.name())
          .register(registry)
          .increment();
    }
    return found;
  }
}
