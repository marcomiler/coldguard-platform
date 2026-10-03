package com.coldguard.commons.messaging;

/** AMQP header names shared by the outbox relay and the consumers. */
public final class MessagingHeaders {

  public static final String CORRELATION_ID = "correlation-id";
  public static final String TRACEPARENT = "traceparent";
  public static final String EVENT_VERSION = "event-version";

  private MessagingHeaders() {}
}
