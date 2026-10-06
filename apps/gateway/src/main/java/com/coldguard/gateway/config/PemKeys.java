package com.coldguard.gateway.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads RSA keys from PEM files: PKCS#8 private keys and X.509 (SubjectPublicKeyInfo) public keys.
 */
final class PemKeys {

  private PemKeys() {}

  static RSAPrivateKey readPrivateKey(Path path) {
    try {
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA")
              .generatePrivate(new PKCS8EncodedKeySpec(decode(path, "PRIVATE KEY")));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Not a valid PKCS#8 RSA private key: " + path, e);
    }
  }

  static RSAPublicKey readPublicKey(Path path) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(decode(path, "PUBLIC KEY")));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Not a valid X.509 RSA public key: " + path, e);
    }
  }

  private static byte[] decode(Path path, String label) {
    String pem;
    try {
      pem = Files.readString(path, StandardCharsets.US_ASCII);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read key file: " + path, e);
    }
    String begin = "-----BEGIN " + label + "-----";
    String end = "-----END " + label + "-----";
    int from = pem.indexOf(begin);
    int to = pem.indexOf(end);
    if (from < 0 || to < from) {
      throw new IllegalStateException("Expected a PEM '" + label + "' block in " + path);
    }
    String body = pem.substring(from + begin.length(), to).replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
