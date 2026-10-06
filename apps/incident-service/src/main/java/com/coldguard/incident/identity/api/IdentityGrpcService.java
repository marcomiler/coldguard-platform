package com.coldguard.incident.identity.api;

import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.VerifyCredentialsRequest;
import com.coldguard.incident.identity.application.AuthenticationResult;
import com.coldguard.incident.identity.application.VerifyCredentialsService;
import com.coldguard.incident.identity.domain.Role;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Comparator;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint of Identity & Access (contracts/grpc/identity/v1). Requests carry passwords: they
 * are never logged, and a failed login is always the same generic UNAUTHENTICATED.
 */
@GrpcService
public class IdentityGrpcService extends IdentityServiceGrpc.IdentityServiceImplBase {

  static final String INVALID_CREDENTIALS = "Invalid credentials";

  private final VerifyCredentialsService verifyCredentials;

  public IdentityGrpcService(VerifyCredentialsService verifyCredentials) {
    this.verifyCredentials = verifyCredentials;
  }

  @Override
  public void verifyCredentials(
      VerifyCredentialsRequest request, StreamObserver<AuthenticatedUser> responseObserver) {
    switch (verifyCredentials.verify(request.getUsername(), request.getPassword())) {
      case AuthenticationResult.Authenticated authenticated -> {
        var user = authenticated.user();
        AuthenticatedUser.Builder reply =
            AuthenticatedUser.newBuilder()
                .setUserId(user.userId().toString())
                .setUsername(user.username())
                .setDisplayName(user.displayName());
        user.roles().stream()
            .sorted(Comparator.comparing(Role::name))
            .forEach(role -> reply.addRoles(toGrpc(role)));
        responseObserver.onNext(reply.build());
        responseObserver.onCompleted();
      }
      case AuthenticationResult.Rejected rejected ->
          responseObserver.onError(
              Status.UNAUTHENTICATED.withDescription(INVALID_CREDENTIALS).asRuntimeException());
    }
  }

  private static com.coldguard.identity.grpc.v1.Role toGrpc(Role role) {
    return switch (role) {
      case OPERATIONS_SUPERVISOR -> com.coldguard.identity.grpc.v1.Role.OPERATIONS_SUPERVISOR;
      case OPERATOR -> com.coldguard.identity.grpc.v1.Role.OPERATOR;
      case MAINTENANCE_TECHNICIAN -> com.coldguard.identity.grpc.v1.Role.MAINTENANCE_TECHNICIAN;
      case AUDITOR -> com.coldguard.identity.grpc.v1.Role.AUDITOR;
      case PLATFORM_ADMIN -> com.coldguard.identity.grpc.v1.Role.PLATFORM_ADMIN;
    };
  }
}
