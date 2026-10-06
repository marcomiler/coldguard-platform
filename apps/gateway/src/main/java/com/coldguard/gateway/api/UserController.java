package com.coldguard.gateway.api;

import com.coldguard.gateway.api.common.PageResponse;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.UserAdministrationException;
import org.springframework.http.HttpStatus;
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
 * SecurityConfig} and again by Identity from the propagated actor. No business rule lives here.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final IdentityGrpcClient identity;

  public UserController(IdentityGrpcClient identity) {
    this.identity = identity;
  }

  @GetMapping
  public PageResponse<UserHttpResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var result = identity.listUsers(page, size);
    return new PageResponse<>(
        result.users().stream().map(UserHttpResponse::of).toList(),
        new PageResponse.PageInfo(
            result.page(), result.size(), result.totalElements(), result.totalPages()));
  }

  @GetMapping("/{userId}")
  public UserHttpResponse get(@PathVariable String userId) {
    return UserHttpResponse.of(identity.getUser(userId));
  }

  @PostMapping
  public ResponseEntity<UserHttpResponse> create(@RequestBody CreateUserHttpRequest request) {
    var user =
        identity.createUser(
            request.username(),
            request.email(),
            request.displayName(),
            request.initialPassword(),
            request.roles());
    return ResponseEntity.status(HttpStatus.CREATED).body(UserHttpResponse.of(user));
  }

  @PostMapping("/{userId}/roles")
  public UserHttpResponse assignRole(
      @PathVariable String userId, @RequestBody RoleChangeHttpRequest request) {
    return UserHttpResponse.of(identity.assignRole(userId, request.role(), request.reason()));
  }

  @DeleteMapping("/{userId}/roles")
  public UserHttpResponse revokeRole(
      @PathVariable String userId, @RequestBody RoleChangeHttpRequest request) {
    return UserHttpResponse.of(identity.revokeRole(userId, request.role(), request.reason()));
  }

  @PostMapping("/{userId}/enabled")
  public UserHttpResponse setEnabled(
      @PathVariable String userId, @RequestBody EnabledHttpRequest request) {
    if (request.enabled() == null) {
      throw new UserAdministrationException(
          UserAdministrationException.Kind.INVALID, "enabled is required");
    }
    return UserHttpResponse.of(
        identity.setUserEnabled(userId, request.enabled(), request.reason()));
  }
}
