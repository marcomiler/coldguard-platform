package com.coldguard.notification;

import java.security.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {

  public static void main(String[] args) {
    // The JVM remembers a failed name lookup for 10 seconds by default. While the mail server is
    // down its name does not resolve, so a restart would go unnoticed for the whole retry window
    // and the notification would be given up on although the server is back. Must be set before
    // the first lookup.
    Security.setProperty("networkaddress.cache.negative.ttl", "0");
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
