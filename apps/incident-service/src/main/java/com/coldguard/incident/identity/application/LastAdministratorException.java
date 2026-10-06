package com.coldguard.incident.identity.application;

/** The change would leave the platform without any enabled PLATFORM_ADMIN. */
public class LastAdministratorException extends RuntimeException {

  public LastAdministratorException() {
    super("The last enabled platform administrator cannot lose that role or be disabled");
  }
}
