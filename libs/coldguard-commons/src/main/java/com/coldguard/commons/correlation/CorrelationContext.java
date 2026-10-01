package com.coldguard.commons.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Single place that knows how the correlation id is named, validated and stored in the MDC. */
public final class CorrelationContext {

  public static final String MDC_KEY = "correlationId";
  public static final String HTTP_HEADER = "X-Correlation-Id";
  public static final String GRPC_METADATA_KEY = "x-correlation-id";

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

  private CorrelationContext() {}

  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }

  public static void set(String correlationId) {
    MDC.put(MDC_KEY, correlationId);
  }

  public static void clear() {
    MDC.remove(MDC_KEY);
  }

  public static String newId() {
    return UUID.randomUUID().toString();
  }

  /** Returns the candidate if it is safe to log and propagate, otherwise a new id. */
  public static String sanitizeOrGenerate(String candidate) {
    return candidate != null && VALID.matcher(candidate).matches() ? candidate : newId();
  }
}
