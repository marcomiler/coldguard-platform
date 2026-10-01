package com.coldguard.gateway.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates an in-memory, self-signed CA and leaf certificates it signs, exclusively for the mTLS
 * handshake tests. Nothing is ever written to disk: certificates and private keys exist only as PEM
 * byte arrays for the lifetime of a single test method, never persisted, never committed.
 */
public final class EphemeralCertificateAuthority {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  /**
   * A leaf certificate and its private key, PEM-encoded, ready for {@code
   * TlsServerCredentials}/{@code TlsChannelCredentials}.
   */
  public record IssuedCertificate(byte[] certificatePem, byte[] privateKeyPem) {

    public ByteArrayInputStream certificateStream() {
      return new ByteArrayInputStream(certificatePem);
    }

    public ByteArrayInputStream privateKeyStream() {
      return new ByteArrayInputStream(privateKeyPem);
    }
  }

  private final X500Name subject;
  private final PrivateKey caPrivateKey;
  private final byte[] caCertificatePem;

  private EphemeralCertificateAuthority(
      X500Name subject, PrivateKey caPrivateKey, byte[] caCertificatePem) {
    this.subject = subject;
    this.caPrivateKey = caPrivateKey;
    this.caCertificatePem = caCertificatePem;
  }

  public static EphemeralCertificateAuthority generate() {
    try {
      KeyPair caKeyPair = generateKeyPair();
      X500Name subject = new X500Name("CN=coldguard-test-ca");
      X509Certificate caCertificate = selfSignCa(subject, caKeyPair);
      return new EphemeralCertificateAuthority(
          subject, caKeyPair.getPrivate(), toPem(caCertificate));
    } catch (GeneralSecurityException | IOException ex) {
      throw new IllegalStateException("Failed to generate ephemeral test CA", ex);
    }
  }

  /** The CA's own certificate, PEM-encoded — used as the trust anchor by server and client. */
  public ByteArrayInputStream certificateStream() {
    return new ByteArrayInputStream(caCertificatePem);
  }

  /** Issues a leaf certificate signed by this CA, valid for both server and client auth. */
  public IssuedCertificate issueLeafCertificate(String commonName, String... sanDnsNames) {
    try {
      KeyPair leafKeyPair = generateKeyPair();
      X509Certificate leafCertificate = signLeaf(commonName, leafKeyPair.getPublic(), sanDnsNames);
      return new IssuedCertificate(toPem(leafCertificate), toPem(leafKeyPair.getPrivate()));
    } catch (GeneralSecurityException | IOException ex) {
      throw new IllegalStateException("Failed to issue ephemeral test leaf certificate", ex);
    }
  }

  private static X509Certificate selfSignCa(X500Name subject, KeyPair caKeyPair)
      throws GeneralSecurityException, IOException {
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject, randomSerial(), notBefore(), notAfter(), subject, caKeyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
    return sign(builder, caKeyPair.getPrivate());
  }

  private X509Certificate signLeaf(String commonName, PublicKey publicKey, String[] sanDnsNames)
      throws GeneralSecurityException, IOException {
    X500Name leafSubject = new X500Name("CN=" + commonName);
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject, randomSerial(), notBefore(), notAfter(), leafSubject, publicKey);
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
    builder.addExtension(
        Extension.extendedKeyUsage,
        false,
        new ExtendedKeyUsage(
            new KeyPurposeId[] {KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth}));
    if (sanDnsNames.length > 0) {
      GeneralName[] names = new GeneralName[sanDnsNames.length];
      for (int i = 0; i < sanDnsNames.length; i++) {
        names[i] = new GeneralName(GeneralName.dNSName, sanDnsNames[i]);
      }
      builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
    }
    return sign(builder, caPrivateKey);
  }

  private static X509Certificate sign(JcaX509v3CertificateBuilder builder, PrivateKey signingKey)
      throws GeneralSecurityException {
    try {
      ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signingKey);
      return new JcaX509CertificateConverter()
          .setProvider(new BouncyCastleProvider())
          .getCertificate(builder.build(signer));
    } catch (org.bouncycastle.operator.OperatorCreationException ex) {
      throw new GeneralSecurityException("Failed to sign ephemeral test certificate", ex);
    }
  }

  private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static BigInteger randomSerial() {
    return new BigInteger(64, new SecureRandom());
  }

  private static Date notBefore() {
    return Date.from(Instant.now().minus(Duration.ofMinutes(5)));
  }

  private static Date notAfter() {
    return Date.from(Instant.now().plus(Duration.ofHours(1)));
  }

  private static byte[] toPem(Object certificateOrKey) throws IOException {
    StringWriter writer = new StringWriter();
    try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(certificateOrKey);
    }
    return writer.toString().getBytes(StandardCharsets.UTF_8);
  }
}
