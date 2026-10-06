package com.coldguard.gateway.api;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserView;
import java.util.List;

public record UserHttpResponse(
    String userId,
    String username,
    String email,
    String displayName,
    List<String> roles,
    boolean enabled,
    String createdAt) {

  static UserHttpResponse of(UserView user) {
    return new UserHttpResponse(
        user.userId(),
        user.username(),
        user.email(),
        user.displayName(),
        user.roles(),
        user.enabled(),
        user.createdAt());
  }
}
