package com.coldguard.gateway.infrastructure;

import com.coldguard.gateway.testsupport.EphemeralCertificateAuthority;
import com.coldguard.gateway.testsupport.EphemeralCertificateAuthority.IssuedCertificate;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-network mTLS handshake tests: a Netty gRPC server/client
 * pair with ephemeral,
 * in-memory certificates ({@link EphemeralCertificateAuthority}) — unlike every
 * other gRPC test
 * in this module, which uses
 * {@code InProcessServerBuilder}/{@code InProcessChannelBuilder} and
 * never negotiates TLS at all. Complements, and does not replace, those
 * in-process tests
 * (unaffected by this class).
 *
 * <p>
 * No certificate or private key is ever written to disk. No shared/static state
 * between
 * tests: each test method generates its own CA and certificates and starts its
 * own server on an
 * OS-assigned ephemeral port ({@code NettyServerBuilder.forPort(0, ...)}), torn
 * down in
 * {@code @AfterEach} before the next test runs. No Docker, no external service.
 *
 * <p>
 * Hostname verification is never disabled: the wrong-SAN case is expected to
 * fail precisely
 * because verification runs normally.
 */
class MutualTlsHandshakeTest {

    private Server server;
    private ManagedChannel channel;

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
    void handshake_validClientCertificateTrustedByServer_succeeds() throws Exception {
        EphemeralCertificateAuthority ca = EphemeralCertificateAuthority.generate();
        IssuedCertificate serverCert = ca.issueLeafCertificate("incident-service", "localhost");
        IssuedCertificate clientCert = ca.issueLeafCertificate("gateway", "gateway-client");

        startServer(ca, serverCert);
        IncidentServiceGrpc.IncidentServiceBlockingStub stub = startClient(ca, clientCert);

        CloseIncidentResponse response = stub.closeIncident(closeRequest());

        assertThat(response.getStatus()).isEqualTo(IncidentStatus.CLOSED);
    }

    @Test
    void handshake_clientWithoutCertificate_rejectedAsTransportError() throws Exception {
        EphemeralCertificateAuthority ca = EphemeralCertificateAuthority.generate();
        IssuedCertificate serverCert = ca.issueLeafCertificate("incident-service", "localhost");

        startServer(ca, serverCert);
        IncidentServiceGrpc.IncidentServiceBlockingStub stub = startClient(ca, null);

        assertUnavailable(stub);
    }

    @Test
    void handshake_serverCertificateFromUntrustedCa_rejectedAsTransportError() throws Exception {
        EphemeralCertificateAuthority untrustedCa = EphemeralCertificateAuthority.generate();
        EphemeralCertificateAuthority trustedCa = EphemeralCertificateAuthority.generate();
        IssuedCertificate serverCert = untrustedCa.issueLeafCertificate("incident-service", "localhost");
        IssuedCertificate clientCert = trustedCa.issueLeafCertificate("gateway", "gateway-client");

        startServer(untrustedCa, serverCert);
        // Client trusts trustedCa only — not the CA that actually signed the server
        // certificate.
        IncidentServiceGrpc.IncidentServiceBlockingStub stub = startClient(trustedCa, clientCert);

        assertUnavailable(stub);
    }

    @Test
    void handshake_serverCertificateWrongSan_rejectedAsTransportError() throws Exception {
        EphemeralCertificateAuthority ca = EphemeralCertificateAuthority.generate();
        IssuedCertificate serverCert = ca.issueLeafCertificate("incident-service", "wrong-host.invalid");
        IssuedCertificate clientCert = ca.issueLeafCertificate("gateway", "gateway-client");

        startServer(ca, serverCert);
        // Client connects to "localhost", but the server certificate's only SAN is
        // "wrong-host.invalid" — hostname verification (never disabled here) must
        // reject this.
        IncidentServiceGrpc.IncidentServiceBlockingStub stub = startClient(ca, clientCert);

        assertUnavailable(stub);
    }

    private static void assertUnavailable(IncidentServiceGrpc.IncidentServiceBlockingStub stub) {
        assertThatThrownBy(() -> stub.closeIncident(closeRequest()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    private static CloseIncidentRequest closeRequest() {
        return CloseIncidentRequest.newBuilder().setIncidentId("incident-1").build();
    }

    private void startServer(EphemeralCertificateAuthority trustAnchor, IssuedCertificate serverCert) throws Exception {
        ServerCredentials credentials = TlsServerCredentials.newBuilder()
                .keyManager(serverCert.certificateStream(), serverCert.privateKeyStream())
                .trustManager(trustAnchor.certificateStream())
                .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
                .build();
        server = NettyServerBuilder.forPort(0, credentials)
                .addService(new IncidentServiceGrpc.IncidentServiceImplBase() {
                    @Override
                    public void closeIncident(CloseIncidentRequest request,
                            StreamObserver<CloseIncidentResponse> responseObserver) {
                        responseObserver.onNext(CloseIncidentResponse.newBuilder()
                                .setIncidentId(request.getIncidentId())
                                .setStatus(IncidentStatus.CLOSED)
                                .build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
    }

    /**
     * {@code clientCert} may be null to exercise the "no client certificate
     * presented" case.
     */
    private IncidentServiceGrpc.IncidentServiceBlockingStub startClient(
            EphemeralCertificateAuthority trustAnchor, IssuedCertificate clientCert) throws Exception {
        TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder()
                .trustManager(trustAnchor.certificateStream());
        if (clientCert != null) {
            builder.keyManager(clientCert.certificateStream(), clientCert.privateKeyStream());
        }
        ChannelCredentials credentials = builder.build();
        channel = NettyChannelBuilder.forAddress("localhost", server.getPort(), credentials).build();
        return IncidentServiceGrpc.newBlockingStub(channel);
    }
}
