package com.coldguard.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.coldguard.gateway.testsupport.TestJwtKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * {@code contracts/rest/openapi.yaml} is what the frontend builds against, so it must not drift
 * from the code: the operations marked {@code implemented} are exactly the ones the Gateway serves,
 * the roles it lists are the ones the real security chain enforces, and nothing planned is served.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

  private static final TestJwtKeys KEYS = TestJwtKeys.generate();
  private static final String API = "/api/v1";
  private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
    registry.add("coldguard.gateway.technical-endpoints.enabled", () -> "true");
    // Downstream services are not running here: closed ports fail fast (never 401/403).
    registry.add("spring.grpc.client.channels.asset-service.address", () -> "static://localhost:1");
    registry.add(
        "spring.grpc.client.channels.incident-service.address", () -> "static://localhost:1");
    registry.add("coldguard.gateway.downstream.asset-service.deadline", () -> "500ms");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private RequestMappingHandlerMapping handlerMapping;

  /** One documented operation. */
  private record Operation(String method, String path, Map<String, Object> definition) {
    String status() {
      return (String) definition.get("x-status");
    }

    @SuppressWarnings("unchecked")
    List<String> roles() {
      return (List<String>) definition.getOrDefault("x-roles", List.of());
    }

    String key() {
      return method.toUpperCase() + " " + API + normalize(path);
    }

    String concretePath() {
      return API + path.replaceAll("\\{[^}]+}", "x");
    }
  }

  private static String normalize(String path) {
    return path.replaceAll("\\{[^}]+}", "{}");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> contract() throws IOException {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null && !Files.exists(dir.resolve("contracts/rest/openapi.yaml"))) {
      dir = dir.getParent();
    }
    assertThat(dir).as("contracts/rest/openapi.yaml above the working directory").isNotNull();
    return new Yaml().load(Files.readString(dir.resolve("contracts/rest/openapi.yaml")));
  }

  @SuppressWarnings("unchecked")
  private static List<Operation> operations() throws IOException {
    List<Operation> operations = new ArrayList<>();
    Map<String, Object> paths = (Map<String, Object>) contract().get("paths");
    paths.forEach(
        (path, item) ->
            ((Map<String, Object>) item)
                .forEach(
                    (method, definition) -> {
                      if (METHODS.contains(method)) {
                        operations.add(
                            new Operation(method, path, (Map<String, Object>) definition));
                      }
                    }));
    return operations;
  }

  /** Every route the Gateway serves under /api/v1, as "METHOD /api/v1/path/{}". */
  private Set<String> servedRoutes() {
    Set<String> served = new TreeSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      Set<String> patterns =
          info.getPathPatternsCondition() == null
              ? Set.of()
              : info.getPathPatternsCondition().getPatternValues();
      for (String pattern : patterns) {
        if (!pattern.startsWith(API + "/")) {
          continue;
        }
        for (var method : info.getMethodsCondition().getMethods()) {
          served.add(method.name() + " " + normalize(pattern));
        }
      }
    }
    return served;
  }

  @Test
  void theImplementedOperationsAreExactlyTheOnesTheGatewayServes() throws IOException {
    Set<String> documented = new TreeSet<>();
    for (Operation operation : operations()) {
      if ("implemented".equals(operation.status())) {
        documented.add(operation.key());
      }
    }

    assertThat(servedRoutes())
        .as("routes served by the Gateway vs operations marked implemented in openapi.yaml")
        .containsExactlyInAnyOrderElementsOf(documented);
  }

  @Test
  void aPlannedOperationIsNotServedYet() throws IOException {
    Set<String> served = servedRoutes();
    for (Operation operation : operations()) {
      if ("planned".equals(operation.status())) {
        assertThat(served)
            .as("%s is served: mark it x-status: implemented", operation.key())
            .doesNotContain(operation.key());
      }
    }
  }

  @Test
  void everyOperationDeclaresItsStatusIdAndRoles() throws IOException {
    @SuppressWarnings("unchecked")
    List<String> validRoles =
        (List<String>)
            ((Map<String, Object>)
                    ((Map<String, Object>)
                            ((Map<String, Object>) contract().get("components")).get("schemas"))
                        .get("Role"))
                .get("enum");
    Set<String> ids = new TreeSet<>();

    for (Operation operation : operations()) {
      assertThat(operation.status())
          .as(operation.key() + " x-status")
          .isIn("implemented", "planned");
      if ("planned".equals(operation.status())) {
        assertThat(operation.definition()).as(operation.key()).containsKey("x-planned-in");
      }
      assertThat(operation.definition().get("operationId")).as(operation.key()).isNotNull();
      assertThat(ids.add((String) operation.definition().get("operationId")))
          .as("operationId of %s is unique", operation.key())
          .isTrue();
      assertThat(operation.definition()).as(operation.key()).containsKey("x-roles");
      assertThat(validRoles).as(operation.key() + " roles").containsAll(operation.roles());
      boolean isPublic = operation.roles().isEmpty();
      assertThat(
              operation.definition().containsKey("security")
                  && ((List<?>) operation.definition().get("security")).isEmpty())
          .as("%s is public exactly when it has no roles", operation.key())
          .isEqualTo(isPublic);
    }
  }

  @Test
  void theRolesOfEachImplementedOperationAreTheOnesTheSecurityChainEnforces() throws Exception {
    @SuppressWarnings("unchecked")
    List<String> allRoles =
        (List<String>)
            ((Map<String, Object>)
                    ((Map<String, Object>)
                            ((Map<String, Object>) contract().get("components")).get("schemas"))
                        .get("Role"))
                .get("enum");

    for (Operation operation : operations()) {
      if (!"implemented".equals(operation.status()) || operation.roles().isEmpty()) {
        continue;
      }
      HttpMethod method = HttpMethod.valueOf(operation.method().toUpperCase());
      for (String role : allRoles) {
        int status =
            mockMvc
                .perform(
                    request(method, operation.concretePath())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andReturn()
                .getResponse()
                .getStatus();
        if (operation.roles().contains(role)) {
          assertThat(status).as("%s as %s (allowed)", operation.key(), role).isNotIn(401, 403);
        } else {
          assertThat(status).as("%s as %s (not allowed)", operation.key(), role).isEqualTo(403);
        }
      }
      assertThat(
              mockMvc
                  .perform(request(method, operation.concretePath()))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .as("%s without a token", operation.key())
          .isEqualTo(401);
    }
  }

  @Test
  void everyReferenceResolvesAndNoSharedSchemaUsesAProtocolPrefix() throws IOException {
    Map<String, Object> contract = contract();
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> components =
        (Map<String, Map<String, Object>>) contract.get("components");
    List<String> references = new ArrayList<>();
    collect(contract, references);

    for (String reference : references) {
      assertThat(reference).startsWith("#/components/");
      String[] parts = reference.substring("#/components/".length()).split("/");
      assertThat(components.get(parts[0])).as(reference).containsKey(parts[1]);
    }

    List<String> prefixed = new ArrayList<>();
    components
        .get("schemas")
        .forEach(
            (name, schema) -> {
              if (!name.startsWith("Legacy")) {
                collectEnumValues(schema, prefixed);
              }
            });
    assertThat(prefixed)
        .as("enum values with a protocol prefix outside the Legacy* schemas")
        .allSatisfy(
            value ->
                assertThat(value)
                    .doesNotStartWith("CRITICALITY_")
                    .doesNotStartWith("SENSOR_STATUS_")
                    .doesNotStartWith("CALIBRATION_KIND_")
                    .doesNotStartWith("IMPACT_")
                    .doesNotStartWith("URGENCY_")
                    .doesNotStartWith("MAGNITUDE_")
                    .doesNotStartWith("READING_SOURCE_"));
  }

  private static void collect(Object node, List<String> references) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) -> {
            if ("$ref".equals(key) && value instanceof String reference) {
              references.add(reference);
            } else {
              collect(value, references);
            }
          });
    } else if (node instanceof List<?> list) {
      list.forEach(item -> collect(item, references));
    }
  }

  private static void collectEnumValues(Object node, List<String> values) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) -> {
            if ("enum".equals(key) && value instanceof List<?> list) {
              list.forEach(item -> values.add(String.valueOf(item)));
            } else {
              collectEnumValues(value, values);
            }
          });
    } else if (node instanceof List<?> list) {
      list.forEach(item -> collectEnumValues(item, values));
    }
  }
}
