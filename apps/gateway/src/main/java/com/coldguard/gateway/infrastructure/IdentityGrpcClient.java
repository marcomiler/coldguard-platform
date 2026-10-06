package com.coldguard.gateway.infrastructure;

import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.Role;
import com.coldguard.identity.grpc.v1.VerifyCredentialsRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin adapter over the Identity gRPC stub. The request carries the password: it is passed through
 * and never logged here.
 */
@Component
public class IdentityGrpcClient {

  /** What the login flow needs from a verified user. */
  public record VerifiedUser(String userId, String username, List<String> roles) {}

  private final IdentityServiceGrpc.IdentityServiceBlockingStub stub;
  private final Duration deadline;

  public IdentityGrpcClient(
      IdentityServiceGrpc.IdentityServiceBlockingStub stub,
      @Value("${coldguard.security.login.deadline:3s}") Duration deadline) {
    this.stub = stub;
    this.deadline = deadline;
  }

  public VerifiedUser verifyCredentials(String username, String password) {
    try {
      AuthenticatedUser user =
          stub.withDeadlineAfter(deadline.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
              .verifyCredentials(
                  VerifyCredentialsRequest.newBuilder()
                      .setUsername(username)
                      .setPassword(password)
                      .build());
      List<String> roles =
          user.getRolesList().stream()
              .filter(role -> role != Role.ROLE_UNSPECIFIED && role != Role.UNRECOGNIZED)
              .map(Role::name)
              .toList();
      return new VerifiedUser(user.getUserId(), user.getUsername(), roles);
    } catch (StatusRuntimeException ex) {
      if (ex.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
        throw new InvalidCredentialsException();
      }
      throw new IdentityServiceException("Identity service unavailable", ex);
    }
  }
}
