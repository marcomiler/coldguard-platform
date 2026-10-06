package com.coldguard.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Exercises the real {@link ActorMetadataClientInterceptor} (not a mock of IncidentGrpcClient)
 * against an in-process gRPC server that captures the metadata it actually receives.
 */
class ActorMetadataClientInterceptorTest {

  private static final Metadata.Key<String> ACTOR_ID_KEY =
      Metadata.Key.of("x-actor-id", Metadata.ASCII_STRING_MARSHALLER);

  private static final Metadata.Key<String> ACTOR_ROLES_KEY =
      Metadata.Key.of("x-actor-roles", Metadata.ASCII_STRING_MARSHALLER);

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
  void interceptCall_jwtAuthentication_propagatesSubjectAndOnlyRoleAuthorities() throws Exception {
    setJwtAuthentication("user-1", "FACTOR_BEARER", "ROLE_OPERATIONS_SUPERVISOR", "ROLE_AUDITOR");

    Metadata received = callCloseIncidentThroughInterceptor();

    assertThat(received.get(ACTOR_ID_KEY)).isEqualTo("user-1");
    assertThat(received.get(ACTOR_ROLES_KEY)).isEqualTo("OPERATIONS_SUPERVISOR,AUDITOR");
  }

  @Test
  void interceptCall_nonJwtAuthentication_sendsNoIdentityMetadata() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                "actor", "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));

    Metadata received = callCloseIncidentThroughInterceptor();

    assertThat(received.get(ACTOR_ID_KEY)).isNull();
    assertThat(received.get(ACTOR_ROLES_KEY)).isNull();
  }

  @Test
  void interceptCall_noAuthentication_sendsNoIdentityMetadata() throws Exception {
    Metadata received = callCloseIncidentThroughInterceptor();

    assertThat(received.get(ACTOR_ID_KEY)).isNull();
    assertThat(received.get(ACTOR_ROLES_KEY)).isNull();
  }

  private Metadata callCloseIncidentThroughInterceptor() throws Exception {
    AtomicReference<Metadata> captured = new AtomicReference<>();
    String serverName = "actor-role-client-interceptor-test-" + System.nanoTime();
    ServerInterceptor captureInterceptor =
        new ServerInterceptor() {
          @Override
          public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
              ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            captured.set(headers);
            return Contexts.interceptCall(Context.current(), call, headers, next);
          }
        };
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(
                new IncidentServiceGrpc.IncidentServiceImplBase() {
                  @Override
                  public void closeIncident(
                      CloseIncidentRequest request,
                      StreamObserver<CloseIncidentResponse> responseObserver) {
                    responseObserver.onNext(
                        CloseIncidentResponse.newBuilder()
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
    Channel interceptedChannel =
        ClientInterceptors.intercept(channel, new ActorMetadataClientInterceptor());

    IncidentServiceGrpc.newBlockingStub(interceptedChannel)
        .closeIncident(CloseIncidentRequest.newBuilder().setIncidentId("incident-1").build());
    return captured.get();
  }

  private static void setJwtAuthentication(String subject, String... authorities) {
    List<GrantedAuthority> granted = new ArrayList<>();
    for (String authority : authorities) {
      granted.add(new SimpleGrantedAuthority(authority));
    }
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, granted));
  }
}
