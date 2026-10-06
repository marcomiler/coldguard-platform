package com.coldguard.incident.identity.api;

import com.coldguard.common.grpc.v1.PageInfo;
import com.coldguard.identity.grpc.v1.AssignRoleRequest;
import com.coldguard.identity.grpc.v1.AuthenticatedUser;
import com.coldguard.identity.grpc.v1.CreateUserRequest;
import com.coldguard.identity.grpc.v1.GetUserRequest;
import com.coldguard.identity.grpc.v1.IdentityServiceGrpc;
import com.coldguard.identity.grpc.v1.ListUserContactsRequest;
import com.coldguard.identity.grpc.v1.ListUserContactsResponse;
import com.coldguard.identity.grpc.v1.ListUsersRequest;
import com.coldguard.identity.grpc.v1.ListUsersResponse;
import com.coldguard.identity.grpc.v1.RevokeRoleRequest;
import com.coldguard.identity.grpc.v1.SetUserEnabledRequest;
import com.coldguard.identity.grpc.v1.User;
import com.coldguard.identity.grpc.v1.UserContact;
import com.coldguard.identity.grpc.v1.VerifyCredentialsRequest;
import com.coldguard.incident.identity.application.AuthenticationResult;
import com.coldguard.incident.identity.application.IdentityAccessDeniedException;
import com.coldguard.incident.identity.application.LastAdministratorException;
import com.coldguard.incident.identity.application.ProvisionUserCommand;
import com.coldguard.incident.identity.application.UserAdministrationService;
import com.coldguard.incident.identity.application.UserAlreadyExistsException;
import com.coldguard.incident.identity.application.UserNotFoundException;
import com.coldguard.incident.identity.application.VerifyCredentialsService;
import com.coldguard.incident.identity.domain.Actor;
import com.coldguard.incident.identity.domain.Role;
import com.coldguard.incident.identity.domain.UserAccount;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint of Identity & Access (contracts/grpc/identity/v1). Requests carry passwords: they
 * are never logged, and a failed login is always the same generic UNAUTHENTICATED. User
 * administration reads the caller from {@link ActorServerInterceptor}; the role check itself lives
 * in {@link UserAdministrationService}.
 */
@GrpcService
public class IdentityGrpcService extends IdentityServiceGrpc.IdentityServiceImplBase {

  static final String INVALID_CREDENTIALS = "Invalid credentials";

  private static final Logger log = LoggerFactory.getLogger(IdentityGrpcService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final VerifyCredentialsService verifyCredentials;
  private final UserAdministrationService administration;

  public IdentityGrpcService(
      VerifyCredentialsService verifyCredentials, UserAdministrationService administration) {
    this.verifyCredentials = verifyCredentials;
    this.administration = administration;
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

  @Override
  public void createUser(CreateUserRequest request, StreamObserver<User> responseObserver) {
    respond(
        responseObserver,
        () ->
            toGrpc(
                administration.create(
                    actor(),
                    new ProvisionUserCommand(
                        request.getUsername(),
                        request.getEmail(),
                        request.getDisplayName(),
                        request.getInitialPassword(),
                        toDomain(request.getRolesList()),
                        null))));
  }

  @Override
  public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {
    respond(responseObserver, () -> toGrpc(administration.get(actor(), request.getUserId())));
  }

  @Override
  public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> observer) {
    respond(
        observer,
        () -> {
          int size = request.getPage().getSize();
          var result =
              administration.list(
                  actor(), request.getPage().getPage(), size == 0 ? DEFAULT_PAGE_SIZE : size);
          ListUsersResponse.Builder reply =
              ListUsersResponse.newBuilder()
                  .setPage(
                      PageInfo.newBuilder()
                          .setPage(result.page())
                          .setSize(result.size())
                          .setTotalElements(result.totalElements())
                          .setTotalPages(result.totalPages()));
          result.users().forEach(user -> reply.addUsers(toGrpc(user)));
          return reply.build();
        });
  }

  @Override
  public void assignRole(AssignRoleRequest request, StreamObserver<User> responseObserver) {
    respond(
        responseObserver,
        () ->
            toGrpc(
                administration.assignRole(
                    actor(),
                    request.getUserId(),
                    toDomain(request.getRole()),
                    request.getReason())));
  }

  @Override
  public void revokeRole(RevokeRoleRequest request, StreamObserver<User> responseObserver) {
    respond(
        responseObserver,
        () ->
            toGrpc(
                administration.revokeRole(
                    actor(),
                    request.getUserId(),
                    toDomain(request.getRole()),
                    request.getReason())));
  }

  @Override
  public void setUserEnabled(SetUserEnabledRequest request, StreamObserver<User> responseObserver) {
    respond(
        responseObserver,
        () ->
            toGrpc(
                administration.setEnabled(
                    actor(), request.getUserId(), request.getEnabled(), request.getReason())));
  }

  @Override
  public void listUserContacts(
      ListUserContactsRequest request, StreamObserver<ListUserContactsResponse> observer) {
    respond(
        observer,
        () -> {
          ListUserContactsResponse.Builder reply = ListUserContactsResponse.newBuilder();
          administration
              .listContacts(actor(), toDomain(request.getRole()))
              .forEach(
                  user ->
                      reply.addContacts(
                          UserContact.newBuilder()
                              .setUserId(user.id().toString())
                              .setEmail(user.email())));
          return reply.build();
        });
  }

  private static Actor actor() {
    return ActorServerInterceptor.ACTOR_CONTEXT_KEY.get();
  }

  private static <T> void respond(StreamObserver<T> observer, Supplier<T> action) {
    T reply;
    try {
      reply = action.get();
    } catch (RuntimeException e) {
      observer.onError(toStatus(e).asRuntimeException());
      return;
    }
    observer.onNext(reply);
    observer.onCompleted();
  }

  private static Status toStatus(RuntimeException e) {
    return switch (e) {
      case IdentityAccessDeniedException denied ->
          Status.PERMISSION_DENIED.withDescription(denied.getMessage());
      case UserNotFoundException notFound ->
          Status.NOT_FOUND.withDescription(notFound.getMessage());
      case UserAlreadyExistsException taken ->
          Status.ALREADY_EXISTS.withDescription(taken.getMessage());
      case LastAdministratorException last ->
          Status.FAILED_PRECONDITION.withDescription(last.getMessage());
      case IllegalArgumentException invalid ->
          Status.INVALID_ARGUMENT.withDescription(invalid.getMessage());
      default -> {
        log.error("Unexpected error in identity administration", e);
        yield Status.INTERNAL.withDescription("Unexpected error");
      }
    };
  }

  private static User toGrpc(UserAccount user) {
    User.Builder reply =
        User.newBuilder()
            .setUserId(user.id().toString())
            .setUsername(user.username())
            .setEmail(user.email())
            .setDisplayName(user.displayName())
            .setEnabled(user.enabled())
            .setCreatedAt(
                Timestamp.newBuilder()
                    .setSeconds(user.createdAt().getEpochSecond())
                    .setNanos(user.createdAt().getNano()));
    user.roles().stream()
        .sorted(Comparator.comparing(Role::name))
        .forEach(role -> reply.addRoles(toGrpc(role)));
    return reply.build();
  }

  private static Set<Role> toDomain(Iterable<com.coldguard.identity.grpc.v1.Role> roles) {
    Set<Role> result = EnumSet.noneOf(Role.class);
    roles.forEach(role -> result.add(toDomain(role)));
    return result;
  }

  private static Role toDomain(com.coldguard.identity.grpc.v1.Role role) {
    return switch (role) {
      case OPERATIONS_SUPERVISOR -> Role.OPERATIONS_SUPERVISOR;
      case OPERATOR -> Role.OPERATOR;
      case MAINTENANCE_TECHNICIAN -> Role.MAINTENANCE_TECHNICIAN;
      case AUDITOR -> Role.AUDITOR;
      case PLATFORM_ADMIN -> Role.PLATFORM_ADMIN;
      case ROLE_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalArgumentException("role is required");
    };
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
