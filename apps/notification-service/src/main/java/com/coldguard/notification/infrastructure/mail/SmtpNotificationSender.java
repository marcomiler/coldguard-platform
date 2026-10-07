package com.coldguard.notification.infrastructure.mail;

import com.coldguard.notification.application.NotificationContent;
import com.coldguard.notification.application.NotificationRequest.Recipient;
import com.coldguard.notification.application.NotificationSender;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends one plain-text email per recipient over SMTP (Mailpit locally). The recipient list is never
 * put in a shared {@code To}. A rejected address is permanent; anything else (connection, timeout,
 * temporary refusal) is worth retrying. Only the exception class is reported, never its message,
 * which may carry the address.
 */
@Component
@ConditionalOnProperty(
    name = "coldguard.notification.channel",
    havingValue = "smtp",
    matchIfMissing = true)
class SmtpNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

  private final JavaMailSender mailSender;
  private final String from;

  SmtpNotificationSender(
      JavaMailSender mailSender,
      @Value("${coldguard.notification.from:coldguard-local@example.invalid}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public SendResult send(Recipient recipient, NotificationContent content) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(recipient.email());
      message.setSubject(content.subject());
      message.setText(content.body());
      mailSender.send(message);
      return new SendResult.Sent();
    } catch (RuntimeException e) {
      SendResult result = classify(e);
      log.warn(
          "Mail delivery failed ({}): {}",
          result.getClass().getSimpleName(),
          rootCause(e).getClass().getSimpleName());
      return result;
    }
  }

  private static Throwable rootCause(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    return t;
  }

  static SendResult classify(RuntimeException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof MailParseException
          || t instanceof AddressException
          || (t instanceof SendFailedException failed
              && failed.getInvalidAddresses() != null
              && failed.getInvalidAddresses().length > 0)) {
        return new SendResult.PermanentFailure(e.getClass().getSimpleName());
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return new SendResult.TransientFailure(e.getClass().getSimpleName());
  }
}
