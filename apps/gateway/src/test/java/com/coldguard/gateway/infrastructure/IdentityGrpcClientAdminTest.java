package com.coldguard.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.identity.grpc.v1.AssignRoleRequest;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.Role;
import com.coldguard.identity.grpc.v1.User;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** User administration calls of {@link IdentityGrpcClient} against a scriptable Identity double. */
class IdentityGrpcClientAdminTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private IdentityGrpcClient clientAnswering(
      AtomicReference<AssignRoleRequest> captured, Status failure) throws Exception {
    String name = "identity-admin-" + System.nanoTime();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                new IdentityServiceGrpc.IdentityServiceImplBase() {
                  @Override
                  public void assignRole(
                      AssignRoleRequest request, StreamObserver<User> responseObserver) {
                    captured.set(request);
                    if (failure != null) {
                      responseObserver.onError(failure.asRuntimeException());
                      return;
                    }
                    responseObserver.onNext(
                        User.newBuilder()
                            .setUserId(request.getUserId())
                            .setUsername("marta")
                            .addRoles(Role.OPERATOR)
                            .setEnabled(true)
                            .build());
                    responseObserver.onCompleted();
                  }
                })
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    return new IdentityGrpcClient(
        IdentityServiceGrpc.newBlockingStub(channel), Duration.ofSeconds(3));
  }

  @Test
  void assignRole_sendsTheRequestAndMapsTheUser() throws Exception {
    var captured = new AtomicReference<AssignRoleRequest>();
    var client = clientAnswering(captured, null);

    var user = client.assignRole("u-1", "OPERATOR", "cover");

    assertThat(captured.get().getRole()).isEqualTo(Role.OPERATOR);
    assertThat(captured.get().getReason()).isEqualTo("cover");
    assertThat(user.roles()).containsExactly("OPERATOR");
    assertThat(user.enabled()).isTrue();
  }

  @Test
  void anUnknownRoleNameIsRejectedBeforeCallingIdentity() throws Exception {
    var captured = new AtomicReference<AssignRoleRequest>();
    var client = clientAnswering(captured, null);

    for (String role : new String[] {"ROOT", "", null, "ROLE_UNSPECIFIED", "UNRECOGNIZED"}) {
      assertThatThrownBy(() -> client.assignRole("u-1", role, "x"))
          .isInstanceOfSatisfying(
              UserAdministrationException.class,
              e -> assertThat(e.kind()).isEqualTo(UserAdministrationException.Kind.INVALID));
    }
    assertThat(captured.get()).isNull();
  }

  @Test
  void grpcStatusesMapToAdministrationKinds() throws Exception {
    expect(Status.PERMISSION_DENIED, UserAdministrationException.Kind.FORBIDDEN);
    expect(Status.NOT_FOUND, UserAdministrationException.Kind.NOT_FOUND);
    expect(Status.ALREADY_EXISTS, UserAdministrationException.Kind.ALREADY_EXISTS);
    expect(Status.FAILED_PRECONDITION, UserAdministrationException.Kind.CONFLICT);
    expect(Status.INVALID_ARGUMENT, UserAdministrationException.Kind.INVALID);
  }

  private void expect(Status status, UserAdministrationException.Kind kind) throws Exception {
    var client = clientAnswering(new AtomicReference<>(), status.withDescription("why"));
    assertThatThrownBy(() -> client.assignRole("u-1", "OPERATOR", "x"))
        .isInstanceOfSatisfying(
            UserAdministrationException.class,
            e -> {
              assertThat(e.kind()).isEqualTo(kind);
              assertThat(e.getMessage()).isEqualTo("why");
            });
    tearDown();
  }

  @Test
  void anyOtherFailureIsTheGenericUnavailableError() throws Exception {
    var client = clientAnswering(new AtomicReference<>(), Status.UNAVAILABLE);

    assertThatThrownBy(() -> client.assignRole("u-1", "OPERATOR", "x"))
        .isInstanceOf(IdentityServiceException.class);
  }
}
