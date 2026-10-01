package com.coldguard.gateway.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Minimal in-process OIDC issuer for tests: signs real RS256 JWTs and serves {@code
 * /.well-known/openid-configuration} plus a JWKS endpoint, so {@link
 * org.springframework.security.oauth2.jwt.JwtDecoders#fromIssuerLocation} builds a real {@link
 * org.springframework.security.oauth2.jwt.JwtDecoder} that performs genuine signature, issuer and
 * expiry validation — unlike {@code SecurityMockMvcRequestPostProcessors.jwt()}, which never
 * invokes a decoder at all.
 */
public final class TestJwtIssuer implements AutoCloseable {

  private final HttpServer server;
  private final String issuer;
  private final RSAKey signingKey;

  private TestJwtIssuer(HttpServer server, String issuer, RSAKey signingKey) {
    this.server = server;
    this.issuer = issuer;
    this.signingKey = signingKey;
  }

  public static TestJwtIssuer start() {
    try {
      RSAKey signingKey = generateRsaKey();
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      String issuer = "http://localhost:" + server.getAddress().getPort();
      server.createContext(
          "/.well-known/openid-configuration",
          exchange -> respondJson(exchange, openIdConfigurationJson(issuer)));
      server.createContext("/certs", exchange -> respondJson(exchange, jwkSetJson(signingKey)));
      server.setExecutor(Executors.newSingleThreadExecutor());
      server.start();
      return new TestJwtIssuer(server, issuer, signingKey);
    } catch (NoSuchAlgorithmException | IOException ex) {
      throw new IllegalStateException("Failed to start test JWT issuer", ex);
    }
  }

  public String issuer() {
    return issuer;
  }

  /** Valid token: real issuer, signed with the key published in this issuer's JWKS. */
  public String signedToken(String subject, List<String> roles, Instant expiresAt) {
    return sign(subject, roles, issuer, expiresAt, signingKey);
  }

  /** Token whose {@code iss} claim does not match this issuer — exercises issuer validation. */
  public String signedTokenWithIssuer(
      String subject, List<String> roles, String otherIssuer, Instant expiresAt) {
    return sign(subject, roles, otherIssuer, expiresAt, signingKey);
  }

  /**
   * Token signed with a key never published in this issuer's JWKS — the decoder cannot find a
   * matching {@code kid} and rejects it, exercising signature/key validation.
   */
  public String signedTokenWithWrongSignature(
      String subject, List<String> roles, Instant expiresAt) {
    try {
      return sign(subject, roles, issuer, expiresAt, generateRsaKey());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Failed to generate untrusted signing key", ex);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private static String sign(
      String subject, List<String> roles, String issuer, Instant expiresAt, RSAKey key) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject(subject)
              .claim("roles", roles)
              .issueTime(Date.from(Instant.now()))
              .expirationTime(Date.from(expiresAt))
              .jwtID(UUID.randomUUID().toString())
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException ex) {
      throw new IllegalStateException("Failed to sign test JWT", ex);
    }
  }

  private static RSAKey generateRsaKey() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey(keyPair.getPrivate())
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  private static String openIdConfigurationJson(String issuer) {
    return """
                {"issuer": "%s", "jwks_uri": "%s/certs"}
                """
        .formatted(issuer, issuer);
  }

  private static String jwkSetJson(RSAKey signingKey) {
    return new JWKSet(signingKey.toPublicJWK()).toString();
  }

  private static void respondJson(HttpExchange exchange, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream body = exchange.getResponseBody()) {
      body.write(bytes);
    }
  }
}
