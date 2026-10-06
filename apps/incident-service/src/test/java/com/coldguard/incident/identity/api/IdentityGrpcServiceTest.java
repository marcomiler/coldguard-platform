package com.coldguard.incident.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.identity.grpc.v1.AssignRoleRequest;
import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.CreateUserRequest;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.ListUsersRequest;
import com.coldguard.identity.grpc.v1.Role;
import com.coldguard.identity.grpc.v1.User;
import com.coldguard.identity.grpc.v1.VerifyCredentialsRequest;
import com.coldguard.incident.identity.application.AuthenticationResult;
import com.coldguard.incident.identity.application.IdentityAccessDeniedException;
import com.coldguard.incident.identity.application.LastAdministratorException;
import com.coldguard.incident.identity.application.UserAdministrationService;
import com.coldguard.incident.identity.application.UserNotFoundException;
import com.coldguard.incident.identity.application.VerifyCredentialsService;
import com.coldguard.incident.identity.domain.Actor;
import com.coldguard.incident.identity.domain.UserAccount;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IdentityGrpcServiceTest {

  private final VerifyCredentialsService verify = Mockito.mock(VerifyCredentialsService.class);
  private final UserAdministrationService administration =
      Mockito.mock(UserAdministrationService.class);
  private final AtomicReference<Actor> caller = new AtomicReference<>();
  private Server server;
  private ManagedChannel channel;
  private IdentityServiceGrpc.IdentityServiceBlockingStub stub;

  @BeforeEach
  void start() throws Exception {
    String name = UUID.randomUUID().toString();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                ServerInterceptors.intercept(
                    new IdentityGrpcService(verify, administration), actorFromTestState()))
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

  private ServerInterceptor actorFromTestState() {
    return new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
          ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Context context =
            Context.current().withValue(ActorServerInterceptor.ACTOR_CONTEXT_KEY, caller.get());
        return Contexts.interceptCall(context, call, headers, next);
      }
    };
  }

  private static final Actor ADMIN =
      new Actor("admin-1", Set.of(com.coldguard.incident.identity.domain.Role.PLATFORM_ADMIN));

  private static UserAccount account() {
    return UserAccount.register(
        "marta",
        "marta@example.com",
        "Marta",
        "{bcrypt}x",
        Set.of(
            com.coldguard.incident.identity.domain.Role.AUDITOR,
            com.coldguard.incident.identity.domain.Role.OPERATOR),
        Instant.parse("2026-10-05T12:00:00Z"));
  }

  @Test
  void assignRolePassesTheCallerAndReturnsTheUserWithoutAnySecret() {
    caller.set(ADMIN);
    UserAccount account = account();
    Mockito.when(
            administration.assignRole(
                ADMIN,
                account.id().toString(),
                com.coldguard.incident.identity.domain.Role.OPERATOR,
                "cover"))
        .thenReturn(account);

    User user =
        stub.assignRole(
            AssignRoleRequest.newBuilder()
                .setUserId(account.id().toString())
                .setRole(Role.OPERATOR)
                .setReason("cover")
                .build());

    assertThat(user.getUserId()).isEqualTo(account.id().toString());
    assertThat(user.getRolesList()).containsExactly(Role.AUDITOR, Role.OPERATOR);
    assertThat(user.getEnabled()).isTrue();
    assertThat(user.toString()).doesNotContain("bcrypt");
  }

  @Test
  void createUserPassesTheCallerAndNeverEchoesThePassword() {
    caller.set(ADMIN);
    Mockito.when(administration.create(Mockito.eq(ADMIN), Mockito.any())).thenReturn(account());

    User user =
        stub.createUser(
            CreateUserRequest.newBuilder()
                .setUsername("marta")
                .setEmail("marta@example.com")
                .setDisplayName("Marta")
                .setInitialPassword("long-enough-pw")
                .addRoles(Role.AUDITOR)
                .build());

    assertThat(user.getUsername()).isEqualTo("marta");
    assertThat(user.toString()).doesNotContain("long-enough-pw");
  }

  @Test
  void listUsersAppliesTheDefaultPageSizeAndReportsPaging() {
    caller.set(ADMIN);
    Mockito.when(administration.list(ADMIN, 0, 20))
        .thenReturn(new UserAdministrationService.UserPage(java.util.List.of(account()), 0, 20, 1));

    var reply = stub.listUsers(ListUsersRequest.getDefaultInstance());

    assertThat(reply.getUsersCount()).isEqualTo(1);
    assertThat(reply.getPage().getTotalElements()).isEqualTo(1);
    assertThat(reply.getPage().getTotalPages()).isEqualTo(1);
  }

  @Test
  void applicationExceptionsMapToGrpcStatuses() {
    caller.set(null);
    Mockito.when(administration.get(Mockito.any(), Mockito.eq("a")))
        .thenThrow(new IdentityAccessDeniedException("PLATFORM_ADMIN role required"));
    Mockito.when(administration.get(Mockito.any(), Mockito.eq("b")))
        .thenThrow(new UserNotFoundException("b"));
    Mockito.when(administration.get(Mockito.any(), Mockito.eq("c")))
        .thenThrow(new LastAdministratorException());
    Mockito.when(administration.get(Mockito.any(), Mockito.eq("d")))
        .thenThrow(new IllegalArgumentException("reason is required"));
    Mockito.when(administration.get(Mockito.any(), Mockito.eq("e")))
        .thenThrow(new IllegalStateException("boom: secret detail"));

    assertThat(codeOf("a")).isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(codeOf("b")).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(codeOf("c")).isEqualTo(Status.Code.FAILED_PRECONDITION);
    assertThat(codeOf("d")).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                stub.getUser(
                    com.coldguard.identity.grpc.v1.GetUserRequest.newBuilder()
                        .setUserId("e")
                        .build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
              assertThat(e.getStatus().getDescription()).doesNotContain("secret");
            });
  }

  @Test
  void anUnspecifiedRoleIsInvalid() {
    caller.set(ADMIN);

    assertThatThrownBy(
            () ->
                stub.assignRole(
                    AssignRoleRequest.newBuilder().setUserId("x").setReason("r").build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
  }

  private Status.Code codeOf(String userId) {
    try {
      stub.getUser(
          com.coldguard.identity.grpc.v1.GetUserRequest.newBuilder().setUserId(userId).build());
      return Status.Code.OK;
    } catch (StatusRuntimeException e) {
      return e.getStatus().getCode();
    }
  }
}
