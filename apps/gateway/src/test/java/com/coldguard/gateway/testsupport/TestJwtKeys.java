package com.coldguard.gateway.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** A throwaway RSA key pair written as PEM files, plus a signer for hand-made test tokens. */
public final class TestJwtKeys {

  private final KeyPair keyPair;
  private final Path privateKeyFile;
  private final Path publicKeyFile;

  private TestJwtKeys(KeyPair keyPair, Path dir) {
    this.keyPair = keyPair;
    this.privateKeyFile = dir.resolve("jwt-private.pem");
    this.publicKeyFile = dir.resolve("jwt-public.pem");
    write(privateKeyFile, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
    write(publicKeyFile, "PUBLIC KEY", keyPair.getPublic().getEncoded());
  }

  public static TestJwtKeys generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return new TestJwtKeys(generator.generateKeyPair(), Files.createTempDirectory("jwt-test"));
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new IllegalStateException("Cannot generate test keys", e);
    }
  }

  public Path privateKeyFile() {
    return privateKeyFile;
  }

  public Path publicKeyFile() {
    return publicKeyFile;
  }

  /** A valid token for {@code issuer}, signed with this key pair. */
  public String token(String issuer, String subject, List<String> roles, Instant expiresAt) {
    return sign(issuer, subject, roles, expiresAt, (RSAPrivateKey) keyPair.getPrivate());
  }

  /** A token signed with a different key: the decoder must reject its signature. */
  public static String tokenSignedWithAnotherKey(
      String issuer, String subject, List<String> roles, Instant expiresAt) {
    return generate().token(issuer, subject, roles, expiresAt);
  }

  private static String sign(
      String issuer, String subject, List<String> roles, Instant expiresAt, RSAPrivateKey key) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject(subject)
              .claim("roles", roles)
              .issueTime(Date.from(Instant.now().minusSeconds(1)))
              .expirationTime(Date.from(expiresAt))
              .jwtID(UUID.randomUUID().toString())
              .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot sign test token", e);
    }
  }

  private static void write(Path file, String label, byte[] der) {
    String pem =
        "-----BEGIN %s-----\n%s\n-----END %s-----\n"
            .formatted(
                label, Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der), label);
    try {
      Files.writeString(file, pem);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
