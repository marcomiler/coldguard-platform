package com.coldguard.gateway;

import com.coldguard.gateway.testsupport.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class GatewayApplicationTests {

  private static final TestJwtKeys KEYS = TestJwtKeys.generate();

  @DynamicPropertySource
  static void jwtKeys(DynamicPropertyRegistry registry) {
    registry.add("coldguard.security.jwt.public-key-path", () -> KEYS.publicKeyFile().toString());
    registry.add("coldguard.security.jwt.private-key-path", () -> KEYS.privateKeyFile().toString());
  }

  @Test
  void contextLoads() {}
}
