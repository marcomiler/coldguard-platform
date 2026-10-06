package com.coldguard.gateway.infrastructure;

import com.coldguard.common.grpc.v1.PageRequest;
import com.coldguard.identity.grpc.v1.AssignRoleRequest;
import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.CreateUserRequest;
import com.coldguard.identity.grpc.v1.GetUserRequest;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.ListUsersRequest;
import com.coldguard.identity.grpc.v1.RevokeRoleRequest;
import com.coldguard.identity.grpc.v1.Role;
import com.coldguard.identity.grpc.v1.SetUserEnabledRequest;
import com.coldguard.identity.grpc.v1.User;
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

  /** A user as administrators see it; never carries a password or hash. */
  public record UserView(
      String userId,
      String username,
      String email,
      String displayName,
      List<String> roles,
      boolean enabled,
      String createdAt) {}

  public record UserPage(
      List<UserView> users, int page, int size, long totalElements, int totalPages) {}

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

  public UserPage listUsers(int page, int size) {
    var reply =
        call(
            () ->
                stub()
                    .listUsers(
                        ListUsersRequest.newBuilder()
                            .setPage(PageRequest.newBuilder().setPage(page).setSize(size))
                            .build()));
    return new UserPage(
        reply.getUsersList().stream().map(IdentityGrpcClient::toView).toList(),
        reply.getPage().getPage(),
        reply.getPage().getSize(),
        reply.getPage().getTotalElements(),
        reply.getPage().getTotalPages());
  }

  public UserView getUser(String userId) {
    return toView(
        call(() -> stub().getUser(GetUserRequest.newBuilder().setUserId(userId).build())));
  }

  /** {@code initialPassword} is sensitive: passed through, never logged. */
  public UserView createUser(
      String username,
      String email,
      String displayName,
      String initialPassword,
      List<String> roles) {
    CreateUserRequest.Builder request =
        CreateUserRequest.newBuilder()
            .setUsername(nullToEmpty(username))
            .setEmail(nullToEmpty(email))
            .setDisplayName(nullToEmpty(displayName))
            .setInitialPassword(nullToEmpty(initialPassword));
    (roles == null ? List.<String>of() : roles).forEach(role -> request.addRoles(toRole(role)));
    return toView(call(() -> stub().createUser(request.build())));
  }

  public UserView assignRole(String userId, String role, String reason) {
    AssignRoleRequest request =
        AssignRoleRequest.newBuilder()
            .setUserId(userId)
            .setRole(toRole(role))
            .setReason(nullToEmpty(reason))
            .build();
    return toView(call(() -> stub().assignRole(request)));
  }

  public UserView revokeRole(String userId, String role, String reason) {
    RevokeRoleRequest request =
        RevokeRoleRequest.newBuilder()
            .setUserId(userId)
            .setRole(toRole(role))
            .setReason(nullToEmpty(reason))
            .build();
    return toView(call(() -> stub().revokeRole(request)));
  }

  public UserView setUserEnabled(String userId, boolean enabled, String reason) {
    SetUserEnabledRequest request =
        SetUserEnabledRequest.newBuilder()
            .setUserId(userId)
            .setEnabled(enabled)
            .setReason(nullToEmpty(reason))
            .build();
    return toView(call(() -> stub().setUserEnabled(request)));
  }

  private IdentityServiceGrpc.IdentityServiceBlockingStub stub() {
    return stub.withDeadlineAfter(deadline.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  private static <T> T call(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (StatusRuntimeException ex) {
      String description =
          ex.getStatus().getDescription() == null ? "" : ex.getStatus().getDescription();
      throw switch (ex.getStatus().getCode()) {
        case PERMISSION_DENIED, UNAUTHENTICATED ->
            new UserAdministrationException(
                UserAdministrationException.Kind.FORBIDDEN, description);
        case NOT_FOUND ->
            new UserAdministrationException(
                UserAdministrationException.Kind.NOT_FOUND, description);
        case ALREADY_EXISTS ->
            new UserAdministrationException(
                UserAdministrationException.Kind.ALREADY_EXISTS, description);
        case FAILED_PRECONDITION ->
            new UserAdministrationException(UserAdministrationException.Kind.CONFLICT, description);
        case INVALID_ARGUMENT ->
            new UserAdministrationException(UserAdministrationException.Kind.INVALID, description);
        default -> new IdentityServiceException("Identity service unavailable", ex);
      };
    }
  }

  private static Role toRole(String name) {
    try {
      Role role = Role.valueOf(name == null ? "" : name.strip());
      if (role != Role.ROLE_UNSPECIFIED && role != Role.UNRECOGNIZED) {
        return role;
      }
    } catch (IllegalArgumentException unknown) {
      // falls through to the same error as an unspecified role
    }
    throw new UserAdministrationException(
        UserAdministrationException.Kind.INVALID, "role must be one of the platform roles");
  }

  private static UserView toView(User user) {
    return new UserView(
        user.getUserId(),
        user.getUsername(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRolesList().stream()
            .filter(role -> role != Role.ROLE_UNSPECIFIED && role != Role.UNRECOGNIZED)
            .map(Role::name)
            .toList(),
        user.getEnabled(),
        java.time.Instant.ofEpochSecond(
                user.getCreatedAt().getSeconds(), user.getCreatedAt().getNanos())
            .toString());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
