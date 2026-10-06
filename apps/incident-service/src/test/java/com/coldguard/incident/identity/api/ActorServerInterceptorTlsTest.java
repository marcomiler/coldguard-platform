package com.coldguard.incident.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.testsupport.EphemeralCertificateAuthority;
import com.coldguard.incident.testsupport.EphemeralCertificateAuthority.IssuedCertificate;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Real mTLS handshake against the production {@link ActorServerInterceptor} (no test double for the
 * peer check): proves the transport exposes the peer certificate and that only the {@code gateway}
 * identity is allowed to assert an actor, even when another client holds a CA-signed certificate.
 */
class ActorServerInterceptorTlsTest {

  private Server server;
  private ManagedChannel channel;
  private final AtomicReference<Actor> seenActor = new AtomicReference<>();

  @AfterEach
  void tearDown() throws InterruptedException {
    if (channel != null) {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void actorMetadata_fromGatewayCertificate_isAccepted() throws Exception {
    call("gateway");

    assertThat(seenActor.get()).isEqualTo(new Actor("user-1", Set.of(Role.AUDITOR)));
  }

  @Test
  void actorMetadata_fromOtherCaSignedCertificate_isIgnored() throws Exception {
    call("sensor-simulator");

    assertThat(seenActor.get()).isNull();
  }

  private void call(String clientIdentity) throws Exception {
    EphemeralCertificateAuthority ca = EphemeralCertificateAuthority.generate();
    IssuedCertificate serverCert = ca.issueLeafCertificate("incident-service", "localhost");
    IssuedCertificate clientCert = ca.issueLeafCertificate(clientIdentity, "client");

    server =
        NettyServerBuilder.forPort(
                0,
                TlsServerCredentials.newBuilder()
                    .keyManager(serverCert.certificateStream(), serverCert.privateKeyStream())
                    .trustManager(ca.certificateStream())
                    .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
                    .build())
            .addService(
                ServerInterceptors.intercept(
                    new IncidentServiceGrpc.IncidentServiceImplBase() {
                      @Override
                      public void closeIncident(
                          CloseIncidentRequest request,
                          StreamObserver<CloseIncidentResponse> responseObserver) {
                        seenActor.set(ActorServerInterceptor.ACTOR_CONTEXT_KEY.get());
                        responseObserver.onNext(CloseIncidentResponse.getDefaultInstance());
                        responseObserver.onCompleted();
                      }
                    },
                    new ActorServerInterceptor()))
            .build()
            .start();
    channel =
        NettyChannelBuilder.forAddress(
                "localhost",
                server.getPort(),
                TlsChannelCredentials.newBuilder()
                    .trustManager(ca.certificateStream())
                    .keyManager(clientCert.certificateStream(), clientCert.privateKeyStream())
                    .build())
            .build();
    ClientInterceptor identityHeaders =
        new ClientInterceptor() {
          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, options)) {
              @Override
              public void start(Listener<RespT> listener, Metadata headers) {
                headers.put(ActorServerInterceptor.ACTOR_ID_KEY, "user-1");
                headers.put(ActorServerInterceptor.ACTOR_ROLES_KEY, "AUDITOR");
                super.start(listener, headers);
              }
            };
          }
        };
    IncidentServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, identityHeaders))
        .closeIncident(CloseIncidentRequest.newBuilder().setIncidentId("i1").build());
  }
}
