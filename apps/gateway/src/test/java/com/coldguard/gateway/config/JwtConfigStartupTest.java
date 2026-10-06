package com.coldguard.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.gateway.testsupport.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** The gateway must not start without the signing key pair. */
class JwtConfigStartupTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

  @Test
  void startsWithAConfiguredKeyPair() {
    TestJwtKeys keys = TestJwtKeys.generate();

    runner
        .withPropertyValues(
            "coldguard.security.jwt.issuer=coldguard-test",
            "coldguard.security.jwt.public-key-path=" + keys.publicKeyFile(),
            "coldguard.security.jwt.private-key-path=" + keys.privateKeyFile())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(JwtDecoder.class).hasSingleBean(JwtEncoder.class);
            });
  }

  @Test
  void failsWithoutKeyPaths() {
    runner
        .withPropertyValues(
            "coldguard.security.jwt.issuer=coldguard-test",
            "coldguard.security.jwt.public-key-path=",
            "coldguard.security.jwt.private-key-path=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsWhenAKeyFileIsMissing() {
    runner
        .withPropertyValues(
            "coldguard.security.jwt.issuer=coldguard-test",
            "coldguard.security.jwt.public-key-path=/nonexistent/public.pem",
            "coldguard.security.jwt.private-key-path=/nonexistent/private.pem")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsWithoutIssuer() {
    TestJwtKeys keys = TestJwtKeys.generate();

    runner
        .withPropertyValues(
            "coldguard.security.jwt.public-key-path=" + keys.publicKeyFile(),
            "coldguard.security.jwt.private-key-path=" + keys.privateKeyFile())
        .run(context -> assertThat(context).hasFailed());
  }
}
