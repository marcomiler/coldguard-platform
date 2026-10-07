package com.coldguard.incident.application;

import com.coldguard.commons.security.Role;
import java.util.List;

/** Who to notify; resolved from Identity & Access. */
public interface NotificationRecipients {

  record Recipient(String userId, String email) {}

  /** Enabled users holding {@code role}; empty when there are none. */
  List<Recipient> enabledWithRole(Role role);
}
