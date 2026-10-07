package com.coldguard.incident.auditlog.application;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Builds and normalises the JSON documents kept in audit records. */
public final class AuditJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private AuditJson() {}

  /** {@code object("a", 1, "b", "x")} is {@code {"a":1,"b":"x"}}; null values are left out. */
  public static String object(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      if (keyValues[i + 1] != null) {
        map.put((String) keyValues[i], String.valueOf(keyValues[i + 1]));
      }
    }
    return MAPPER.writeValueAsString(map);
  }

  /** A JSON object is kept as is; any other text is wrapped as {@code {"summary": text}}. */
  static String normalise(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.startsWith("{")) {
      try {
        MAPPER.readTree(trimmed);
        return trimmed;
      } catch (RuntimeException notJson) {
        // fall through: keep it as text
      }
    }
    return object("summary", value);
  }
}
