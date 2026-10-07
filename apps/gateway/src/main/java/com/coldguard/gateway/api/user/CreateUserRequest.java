package com.coldguard.gateway.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Shape only: the password policy is a business rule and lives in Identity. {@code initialPassword}
 * is sensitive: the record never prints it.
 */
public record CreateUserRequest(
    @NotBlank @Size(max = 60) String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Size(max = 128) String initialPassword,
    @NotEmpty List<@NotNull Role> roles) {

  @Override
  public String toString() {
    return "CreateUserRequest[username=" + username + ", roles=" + roles + "]";
  }
}
