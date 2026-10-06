package com.coldguard.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

  // ---- who the caller is ------------------------------------------------------------------

  private static Metadata identity(String id, String roles) {
    Metadata headers = new Metadata();
    if (id != null) {
      headers.put(ActorServerInterceptor.ACTOR_ID_KEY, id);
    }
    if (roles != null) {
      headers.put(ActorServerInterceptor.ACTOR_ROLES_KEY, roles);
    }
    return headers;
  }

  @SuppressWarnings("unchecked")
  private static Actor actorSeenBy(
      ActorServerInterceptor interceptor, Attributes attributes, Metadata headers) {
    ServerCall<String, String> call = mock(ServerCall.class);
    when(call.getAttributes()).thenReturn(attributes);
    AtomicReference<Actor> seen = new AtomicReference<>();
    ServerCallHandler<String, String> next =
        (c, h) -> {
          seen.set(ActorServerInterceptor.ACTOR_CONTEXT_KEY.get());
          return new ServerCall.Listener<>() {};
        };
    interceptor.interceptCall(call, headers, next);
    return seen.get();
  }

  private final ActorServerInterceptor interceptor =
      new ActorServerInterceptor(Set.of("telemetry-service"));

  @Test
  void gateway_propagatesAUserWithRolesAndIgnoresUnknownRoleNames() throws Exception {
    Actor actor =
        actorSeenBy(
            interceptor,
            attributesWithPeer("CN=gateway"),
            identity("user-1", "AUDITOR, ROOT ,PLATFORM_ADMIN"));

    assertThat(actor).isEqualTo(new Actor("user-1", Set.of(Role.AUDITOR, Role.PLATFORM_ADMIN)));
  }

  @Test
  void gateway_withoutAnActorIdRunsWithoutActor() throws Exception {
    assertThat(
            actorSeenBy(interceptor, attributesWithPeer("CN=gateway"), identity(null, "AUDITOR")))
        .isNull();
    assertThat(actorSeenBy(interceptor, attributesWithPeer("CN=gateway"), identity("  ", null)))
        .isNull();
  }

  @Test
  void gateway_cannotAssertASystemActor() throws Exception {
    assertThat(
            actorSeenBy(
                interceptor,
                attributesWithPeer("CN=gateway"),
                identity("system:telemetry-service", "PLATFORM_ADMIN")))
        .isNull();
  }

  @Test
  void configuredSystemCaller_isASystemActorWhateverItClaims() throws Exception {
    Actor actor =
        actorSeenBy(
            interceptor,
            attributesWithPeer("CN=telemetry-service"),
            identity("user-1", "PLATFORM_ADMIN"));

    assertThat(actor).isEqualTo(Actor.system("telemetry-service"));
    assertThat(actor.roles()).isEmpty();
  }

  @Test
  void otherPeers_runWithoutActorEvenWhenTheyClaimOne() throws Exception {
    Metadata claim = identity("user-1", "PLATFORM_ADMIN");

    assertThat(actorSeenBy(interceptor, attributesWithPeer("CN=sensor-simulator"), claim)).isNull();
    assertThat(actorSeenBy(interceptor, attributesWithPeer("CN=telemetry-service-evil"), claim))
        .isNull();
    assertThat(actorSeenBy(interceptor, Attributes.EMPTY, claim)).isNull();
  }

  @Test
  void withoutConfiguredSystemCallers_noPeerIsASystemCaller() throws Exception {
    ActorServerInterceptor defaults = new ActorServerInterceptor();

    assertThat(
            actorSeenBy(defaults, attributesWithPeer("CN=telemetry-service"), identity(null, null)))
        .isNull();
    assertThat(
            actorSeenBy(defaults, attributesWithPeer("CN=gateway"), identity("user-1", "AUDITOR")))
        .isEqualTo(new Actor("user-1", Set.of(Role.AUDITOR)));
  }
}
