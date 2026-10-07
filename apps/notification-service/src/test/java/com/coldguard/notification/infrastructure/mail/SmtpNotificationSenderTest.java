package com.coldguard.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.notification.application.NotificationContent;
import com.coldguard.notification.application.NotificationRequest.Recipient;
import com.coldguard.notification.application.NotificationSender.SendResult;
import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpNotificationSenderTest {

  private static final Recipient TO = new Recipient("u1", "u1@coldguard.test");
  private static final NotificationContent CONTENT = new NotificationContent("s", "b");

  @Test
  void unreachableServerIsTransient() {
    JavaMailSenderImpl mail = new JavaMailSenderImpl();
    mail.setHost("localhost");
    mail.setPort(1); // nothing listens here
    mail.getJavaMailProperties().put("mail.smtp.connectiontimeout", "500");
    mail.getJavaMailProperties().put("mail.smtp.timeout", "500");

    SendResult result = new SmtpNotificationSender(mail, "from@x.test").send(TO, CONTENT);

    assertThat(result).isInstanceOf(SendResult.TransientFailure.class);
  }

  @Test
  void aRejectedAddressIsPermanent() {
    JavaMailSender mail =
        new JavaMailSenderImpl() {
          @Override
          public void send(org.springframework.mail.SimpleMailMessage message) {
            throw new MailSendException(
                "rejected",
                new SendFailedException("550", null, new Address[0], new Address[0], invalid()));
          }
        };

    assertThat(new SmtpNotificationSender(mail, "from@x.test").send(TO, CONTENT))
        .isInstanceOf(SendResult.PermanentFailure.class);
  }

  private static Address[] invalid() {
    try {
      return new Address[] {new InternetAddress("nobody@coldguard.test")};
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void classifyTreatsParseErrorsAsPermanentAndEverythingElseAsTransient() {
    assertThat(SmtpNotificationSender.classify(new MailParseException("bad")))
        .isInstanceOf(SendResult.PermanentFailure.class);
    assertThat(
            SmtpNotificationSender.classify(
                new MailSendException("down", new ConnectException("refused"))))
        .isInstanceOf(SendResult.TransientFailure.class);
  }

  @Test
  void theReportedReasonNeverCarriesTheAddress() {
    SendResult result =
        SmtpNotificationSender.classify(new MailSendException("failed for u1@coldguard.test"));

    assertThat(result.toString()).doesNotContain("coldguard.test");
  }
}
