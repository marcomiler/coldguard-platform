package com.coldguard.incident.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.Role;
import com.coldguard.identity.grpc.v1.VerifyCredentialsRequest;
import com.coldguard.incident.identity.application.AuthenticationResult;
import com.coldguard.incident.identity.application.VerifyCredentialsService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IdentityGrpcServiceTest {

  private final VerifyCredentialsService verify = Mockito.mock(VerifyCredentialsService.class);
  private Server server;
  private ManagedChannel channel;
  private IdentityServiceGrpc.IdentityServiceBlockingStub stub;

  @BeforeEach
  void start() throws Exception {
    String name = UUID.randomUUID().toString();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new IdentityGrpcService(verify))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    stub = IdentityServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void stop() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  private VerifyCredentialsRequest request() {
    return VerifyCredentialsRequest.newBuilder().setUsername("marta").setPassword("pw").build();
  }

  @Test
  void returnsTheAuthenticatedUserWithRoles() {
    UUID id = UUID.randomUUID();
    Mockito.when(verify.verify("marta", "pw"))
        .thenReturn(
            new AuthenticationResult.Authenticated(
                new com.coldguard.incident.identity.application.AuthenticatedUser(
                    id,
                    "marta",
                    "Marta",
                    Set.of(
                        com.coldguard.incident.identity.domain.Role.AUDITOR,
                        com.coldguard.incident.identity.domain.Role.OPERATOR))));

    AuthenticatedUser user = stub.verifyCredentials(request());

    assertThat(user.getUserId()).isEqualTo(id.toString());
    assertThat(user.getRolesList()).containsExactly(Role.AUDITOR, Role.OPERATOR);
  }

  @Test
  void rejectionIsAGenericUnauthenticated() {
    Mockito.when(verify.verify("marta", "pw")).thenReturn(new AuthenticationResult.Rejected());

    assertThatThrownBy(() -> stub.verifyCredentials(request()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
              assertThat(e.getStatus().getDescription())
                  .isEqualTo(IdentityGrpcService.INVALID_CREDENTIALS);
            });
  }
}
