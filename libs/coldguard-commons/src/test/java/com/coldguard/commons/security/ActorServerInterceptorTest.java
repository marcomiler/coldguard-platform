package com.coldguard.incident.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Grpc;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;

class ActorServerInterceptorTest {

  @Test
  void isGatewayPeer_certificateWithGatewayCommonName_isTrusted() throws Exception {
    assertThat(ActorServerInterceptor.isGatewayPeer(attributesWithPeer("CN=gateway"))).isTrue();
  }

  @Test
  void isGatewayPeer_certificateOfAnotherService_isNotTrusted() throws Exception {
    assertThat(ActorServerInterceptor.isGatewayPeer(attributesWithPeer("CN=sensor-simulator")))
        .isFalse();
  }

  @Test
  void isGatewayPeer_commonNameMerelyContainingGateway_isNotTrusted() throws Exception {
    assertThat(
            ActorServerInterceptor.isGatewayPeer(attributesWithPeer("CN=gateway-evil,O=gateway")))
        .isFalse();
  }

  @Test
  void isGatewayPeer_noTlsSession_isNotTrusted() {
    assertThat(ActorServerInterceptor.isGatewayPeer(Attributes.EMPTY)).isFalse();
  }

  @Test
  void isGatewayPeer_unverifiedPeer_isNotTrusted() throws Exception {
    SSLSession session = mock(SSLSession.class);
    when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no client cert"));
    Attributes attributes =
        Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build();

    assertThat(ActorServerInterceptor.isGatewayPeer(attributes)).isFalse();
  }

  private static Attributes attributesWithPeer(String subject) throws Exception {
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal(subject));
    SSLSession session = mock(SSLSession.class);
    when(session.getPeerCertificates()).thenReturn(new Certificate[] {certificate});
    return Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build();
  }
}
