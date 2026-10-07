package com.coldguard.incident.auditlog.infrastructure.messaging;

import com.coldguard.commons.messaging.EnvelopeCodec;
import com.coldguard.commons.messaging.EventEnvelope;
import com.coldguard.commons.messaging.inbox.InboxGuard;
import com.coldguard.incident.auditlog.application.AuditEntry;
import com.coldguard.incident.auditlog.application.AuditRecorder;
import com.coldguard.incident.infrastructure.messaging.IncidentMessagingTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the audit trail of events produced by other services. The source event id is unique in the
 * audit table, so a redelivery cannot duplicate a record; the message is acknowledged only after
 * the record commits.
 */
@Component
class AuditEventConsumer {

  static final String CONSUMER = "incident-service.audit";

  private final EnvelopeCodec codec;
  private final InboxGuard inbox;
  private final AuditEventMapper mapper;
  private final AuditRecorder recorder;

  AuditEventConsumer(
      EnvelopeCodec codec, InboxGuard inbox, AuditEventMapper mapper, AuditRecorder recorder) {
    this.codec = codec;
    this.inbox = inbox;
    this.mapper = mapper;
    this.recorder = recorder;
  }

  @RabbitListener(queues = IncidentMessagingTopology.AUDIT_QUEUE)
  void on(Message message) {
    EventEnvelope event = codec.read(message.getBody(), AuditEventMapper.SUPPORTED);
    AuditEntry entry = mapper.map(event);
    inbox.runOnce(event.eventId(), CONSUMER, () -> recorder.record(entry));
  }
}
