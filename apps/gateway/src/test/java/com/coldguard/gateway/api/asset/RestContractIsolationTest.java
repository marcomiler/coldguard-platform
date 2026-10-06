package com.coldguard.gateway.api.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The REST contract must not depend on the gRPC one: only the mapper and the controllers may touch
 * generated classes, never a type that is serialized to or from a client.
 */
class RestContractIsolationTest {

  private static final Path SOURCES = Path.of("src/main/java/com/coldguard/gateway/api/asset");
  private static final Pattern GENERATED =
      Pattern.compile("import com\\.coldguard\\.[a-z]+\\.grpc\\.");

  @Test
  void noRestTypeImportsAGeneratedGrpcClass() throws IOException {
    assertThat(Files.isDirectory(SOURCES)).as("run from the module directory").isTrue();
    List<String> offenders;
    try (Stream<Path> files = Files.list(SOURCES)) {
      offenders =
          files
              .filter(file -> file.toString().endsWith(".java"))
              .filter(file -> !isGrpcAware(file))
              .filter(
                  file -> {
                    try {
                      return GENERATED.matcher(Files.readString(file)).find();
                    } catch (IOException e) {
                      throw new IllegalStateException(e);
                    }
                  })
              .map(file -> file.getFileName().toString())
              .toList();
    }

    assertThat(offenders).as("REST types importing generated gRPC classes").isEmpty();
  }

  /** The mapper translates; the controllers hold the request-side calls that need a proto id. */
  private static boolean isGrpcAware(Path file) {
    String name = file.getFileName().toString();
    return name.equals("AssetRestMapper.java") || name.endsWith("Controller.java");
  }
}
