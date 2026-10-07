package com.coldguard.notification.application;

import com.coldguard.notification.application.NotificationSender.SendResult;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends the notification to every recipient still pending. Delivery is at least once: if the
 * process dies after a mail leaves and before it is recorded, the redelivery sends it again. Not
 * transactional itself, so the mail server is never called inside a database transaction. Logs
 * identify the request and incident, never an address or a message body.
 */
@Service
public class HandleNotificationRequestedService {

  private static final Logger log =
      LoggerFactory.getLogger(HandleNotificationRequestedService.class);

  private final NotificationLedger ledger;
  private final NotificationTemplateRenderer renderer;
  private final NotificationSender sender;

  HandleNotificationRequestedService(
      NotificationLedger ledger, NotificationTemplateRenderer renderer, NotificationSender sender) {
    this.ledger = ledger;
    this.renderer = renderer;
    this.sender = sender;
  }

  /**
   * @throws TransientDeliveryException if some recipient could not be reached for now
   */
  public void handle(NotificationRequest request) {
    Set<String> pending = ledger.register(request);
    if (pending.isEmpty()) {
      log.info(
          "Notification {} for incident {} already processed",
          request.requestId(),
          request.incidentId());
      return;
    }
    NotificationContent content = renderer.render(request);
    int sent = 0;
    int transientFailures = 0;
    int permanentFailures = 0;
    for (NotificationRequest.Recipient recipient : request.recipients()) {
      if (!pending.contains(recipient.userId())) {
        continue;
      }
      switch (sender.send(recipient, content)) {
        case SendResult.Sent ignored -> {
          ledger.recordSent(request.requestId(), recipient.userId());
          sent++;
        }
        case SendResult.TransientFailure ignored -> {
          ledger.recordTransientFailure(request.requestId(), recipient.userId());
          transientFailures++;
        }
        case SendResult.PermanentFailure ignored -> {
          ledger.recordPermanentFailure(request.requestId(), recipient.userId());
          permanentFailures++;
        }
      }
    }
    log.info(
        "Notification {} ({}) for incident {}: sent={} transientFailures={} permanentFailures={}",
        request.requestId(),
        request.type(),
        request.incidentId(),
        sent,
        transientFailures,
        permanentFailures);
    if (transientFailures > 0) {
      throw new TransientDeliveryException(request.requestId(), transientFailures);
    }
  }
}
