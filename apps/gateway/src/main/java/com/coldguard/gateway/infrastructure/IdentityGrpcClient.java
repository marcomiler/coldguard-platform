package com.coldguard.gateway.infrastructure;

import com.coldguard.common.grpc.v1.PageRequest;
import com.coldguard.gateway.config.DownstreamProperties;
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
import java.util.List;
import java.util.function.Function;
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

  /** Identity & Access is a module of Incident Service and shares its channel and deadline. */
  static final String SERVICE = "incident-service";

  private final IdentityServiceGrpc.IdentityServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public IdentityGrpcClient(
      IdentityServiceGrpc.IdentityServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  private <R> R command(Function<IdentityServiceGrpc.IdentityServiceBlockingStub, R> call) {
    return invoker.call(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  private <R> R query(Function<IdentityServiceGrpc.IdentityServiceBlockingStub, R> call) {
    return invoker.query(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  /**
   * @throws InvalidCredentialsException whatever the reason Identity refused them
   */
  public VerifiedUser verifyCredentials(String username, String password) {
    AuthenticatedUser user;
    try {
      user =
          command(
              s ->
                  s.verifyCredentials(
                      VerifyCredentialsRequest.newBuilder()
                          .setUsername(username)
                          .setPassword(password)
                          .build()));
    } catch (DownstreamCallException ex) {
      if (ex.grpcCode() == Status.Code.UNAUTHENTICATED) {
        throw new InvalidCredentialsException();
      }
      throw ex;
    }
    List<String> roles =
        user.getRolesList().stream()
            .filter(role -> role != Role.ROLE_UNSPECIFIED && role != Role.UNRECOGNIZED)
            .map(Role::name)
            .toList();
    return new VerifiedUser(user.getUserId(), user.getUsername(), roles);
  }

  public UserPage listUsers(int page, int size) {
    var reply =
        query(
            s ->
                s.listUsers(
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
    return toView(query(s -> s.getUser(GetUserRequest.newBuilder().setUserId(userId).build())));
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
    return toView(command(s -> s.createUser(request.build())));
  }

  public UserView assignRole(String userId, String role, String reason) {
    AssignRoleRequest request =
        AssignRoleRequest.newBuilder()
            .setUserId(userId)
            .setRole(toRole(role))
            .setReason(nullToEmpty(reason))
            .build();
    return toView(command(s -> s.assignRole(request)));
  }

  public UserView revokeRole(String userId, String role, String reason) {
    RevokeRoleRequest request =
        RevokeRoleRequest.newBuilder()
            .setUserId(userId)
            .setRole(toRole(role))
            .setReason(nullToEmpty(reason))
            .build();
    return toView(command(s -> s.revokeRole(request)));
  }

  public UserView setUserEnabled(String userId, boolean enabled, String reason) {
    SetUserEnabledRequest request =
        SetUserEnabledRequest.newBuilder()
            .setUserId(userId)
            .setEnabled(enabled)
            .setReason(nullToEmpty(reason))
            .build();
    return toView(command(s -> s.setUserEnabled(request)));
  }

  private static Role toRole(String name) {
    return Role.valueOf(name);
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
