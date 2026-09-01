package com.coldguard.gateway.infrastructure;

import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real {@link ActorRoleClientInterceptor} (not a mock of IncidentGrpcClient)
 * against an in-process gRPC server that captures the metadata it actually receives.
 */
class ActorRoleClientInterceptorTest {

    private static final Metadata.Key<String> ACTOR_ROLE_KEY =
            Metadata.Key.of("x-actor-role", Metadata.ASCII_STRING_MARSHALLER);

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        SecurityContextHolder.clearContext();
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void interceptCall_authoritiesIncludeFactorBearerAndRequiredRole_propagatesOnlyRequiredRole() throws Exception {
        setAuthenticatedAuthorities("FACTOR_BEARER", "ROLE_MAINTENANCE_TECHNICIAN");
        AtomicReference<String> capturedActorRole = new AtomicReference<>();

        callCloseIncidentThroughInterceptor(capturedActorRole);

        assertThat(capturedActorRole.get()).isEqualTo("ROLE_MAINTENANCE_TECHNICIAN");
    }

    @Test
    void interceptCall_requiredRoleAbsent_sendsNoActorRoleMetadata() throws Exception {
        setAuthenticatedAuthorities("FACTOR_BEARER", "ROLE_SUPERVISOR");
        AtomicReference<String> capturedActorRole = new AtomicReference<>();

        callCloseIncidentThroughInterceptor(capturedActorRole);

        assertThat(capturedActorRole.get()).isNull();
    }

    private void callCloseIncidentThroughInterceptor(AtomicReference<String> capturedActorRole) throws Exception {
        String serverName = "actor-role-client-interceptor-test-" + System.nanoTime();
        ServerInterceptor captureInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                capturedActorRole.set(headers.get(ACTOR_ROLE_KEY));
                return Contexts.interceptCall(Context.current(), call, headers, next);
            }
        };
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
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
                .intercept(captureInterceptor)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        Channel interceptedChannel = ClientInterceptors.intercept(channel, new ActorRoleClientInterceptor());

        IncidentServiceGrpc.newBlockingStub(interceptedChannel)
                .closeIncident(CloseIncidentRequest.newBuilder().setIncidentId("incident-1").build());
    }

    private static void setAuthenticatedAuthorities(String... authorities) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (String authority : authorities) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("actor", "n/a", grantedAuthorities));
    }
}
