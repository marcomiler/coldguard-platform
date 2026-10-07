package com.coldguard.gateway.api.user;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserView;
import java.util.List;

public record UserResponse(
    String userId,
    String username,
    String email,
    String displayName,
    List<String> roles,
    boolean enabled,
    String createdAt) {

  static UserResponse of(UserView user) {
    return new UserResponse(
        user.userId(),
        user.username(),
        user.email(),
        user.displayName(),
        user.roles(),
        user.enabled(),
        user.createdAt());
  }
}
