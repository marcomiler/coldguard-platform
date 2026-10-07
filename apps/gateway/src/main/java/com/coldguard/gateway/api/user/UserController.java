package com.coldguard.gateway.api.user;

import com.coldguard.gateway.api.common.PageResponse;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration (CU-014). The role check is enforced twice: by the route table in {@code
 * SecurityConfig} and again by Identity from the propagated actor. Shape validation, mapping and
 * the call; no business rule lives here.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final IdentityGrpcClient identity;

  public UserController(IdentityGrpcClient identity) {
    this.identity = identity;
  }

  @GetMapping
  public PageResponse<UserResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "0") int size) {
    var result = identity.listUsers(page, size);
    return new PageResponse<>(
        result.users().stream().map(UserResponse::of).toList(),
        new PageResponse.PageInfo(
            result.page(), result.size(), result.totalElements(), result.totalPages()));
  }

  @GetMapping("/{userId}")
  public UserResponse get(@PathVariable String userId) {
    return UserResponse.of(identity.getUser(userId));
  }

  @PostMapping
  public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    var user =
        identity.createUser(
            request.username(),
            request.email(),
            request.displayName(),
            request.initialPassword(),
            request.roles().stream().map(Enum::name).toList());
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.userId()))
        .body(UserResponse.of(user));
  }

  @PostMapping("/{userId}/roles")
  public UserResponse assignRole(
      @PathVariable String userId, @Valid @RequestBody RoleChangeRequest request) {
    return UserResponse.of(identity.assignRole(userId, request.role().name(), request.reason()));
  }

  @DeleteMapping("/{userId}/roles")
  public UserResponse revokeRole(
      @PathVariable String userId, @Valid @RequestBody RoleChangeRequest request) {
    return UserResponse.of(identity.revokeRole(userId, request.role().name(), request.reason()));
  }

  @PostMapping("/{userId}/enabled")
  public UserResponse setEnabled(
      @PathVariable String userId, @Valid @RequestBody EnabledRequest request) {
    return UserResponse.of(identity.setUserEnabled(userId, request.enabled(), request.reason()));
  }
}
