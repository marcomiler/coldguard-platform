package com.coldguard.asset.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.messaging.outbox.OutboundEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks an event against its JSON Schema in {@code contracts/events/asset}: the constants of the
 * envelope fields, every required payload field, and the declared type or enum of each one. It is a
 * light contract test; it does not implement the full JSON Schema specification.
 */
public final class EventContract {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();
  private static final Path DIR = locate();

  private EventContract() {}

  public static void assertConforms(OutboundEvent event) {
    JsonNode schema = read(event.eventType());
    JsonNode properties = schema.get("properties");
    assertThat(event.eventType()).isEqualTo(properties.get("eventType").get("const").asString());
    assertThat((long) event.eventVersion())
        .isEqualTo(properties.get("eventVersion").get("const").asLong());
    assertThat(event.aggregateType())
        .isEqualTo(properties.get("aggregateType").get("const").asString());

    JsonNode payloadSchema = properties.get("payload");
    JsonNode payload = MAPPER.valueToTree(event.payload());
    for (JsonNode required : payloadSchema.get("required")) {
      assertThat(payload.has(required.asString()))
          .as("%s payload has required field %s", event.eventType(), required.asString())
          .isTrue();
    }
    payloadSchema
        .get("properties")
        .properties()
        .forEach(
            entry -> {
              if (payload.has(entry.getKey())) {
                assertField(
                    event.eventType(),
                    entry.getKey(),
                    entry.getValue(),
                    payload.get(entry.getKey()));
              }
            });
  }

  private static void assertField(String event, String name, JsonNode schema, JsonNode value) {
    String where = event + "." + name;
    if (schema.has("enum")) {
      boolean member = false;
      for (JsonNode allowed : schema.get("enum")) {
        member |= allowed.asString().equals(value.asString());
      }
      assertThat(member).as("%s is one of the allowed values but was %s", where, value).isTrue();
    }
    if (!schema.has("type")) {
      return;
    }
    java.util.List<String> types = new java.util.ArrayList<>();
    if (schema.get("type").isArray()) {
      schema.get("type").forEach(t -> types.add(t.asString()));
    } else {
      types.add(schema.get("type").asString());
    }
    assertThat(types)
        .as("%s declared type vs value %s", where, value)
        .anyMatch(t -> matches(t, value));
    if (schema.has("minimum")) {
      assertThat(value.asLong())
          .as("%s minimum", where)
          .isGreaterThanOrEqualTo(schema.get("minimum").asLong());
    }
    if (schema.has("minItems")) {
      assertThat(value.size())
          .as("%s minItems", where)
          .isGreaterThanOrEqualTo(schema.get("minItems").asInt());
    }
  }

  private static boolean matches(String type, JsonNode value) {
    return switch (type) {
      case "string" -> value.isString();
      case "integer" -> value.isIntegralNumber();
      case "number" -> value.isNumber();
      case "boolean" -> value.isBoolean();
      case "array" -> value.isArray();
      case "object" -> value.isObject();
      case "null" -> value.isNull();
      default -> false;
    };
  }

  private static JsonNode read(String eventType) {
    try {
      return MAPPER.readTree(Files.readString(DIR.resolve(eventType + ".v1.schema.json")));
    } catch (java.io.IOException e) {
      throw new AssertionError("Cannot read the schema of " + eventType, e);
    }
  }

  private static Path locate() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null && !Files.isDirectory(dir.resolve("contracts/events/asset"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException(
          "contracts/events/asset not found above the working directory");
    }
    return dir.resolve("contracts/events/asset");
  }
}
