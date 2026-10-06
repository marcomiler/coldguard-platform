package com.coldguard.gateway.api;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.InvalidCredentialsException;
import com.coldguard.gateway.infrastructure.JwtTokenIssuer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login (CU-016): Identity verifies the credentials over gRPC and the gateway signs the token.
 * Anything wrong with the input is the same 401 as a wrong password.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final int MAX_FIELD_LENGTH = 128;

  private final IdentityGrpcClient identity;
  private final JwtTokenIssuer tokens;

  public AuthController(IdentityGrpcClient identity, JwtTokenIssuer tokens) {
    this.identity = identity;
    this.tokens = tokens;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginHttpResponse> login(@RequestBody LoginHttpRequest request) {
    if (isInvalid(request.username()) || isInvalid(request.password())) {
      throw new InvalidCredentialsException();
    }
    var user = identity.verifyCredentials(request.username(), request.password());
    var token = tokens.issue(user.userId(), user.username(), user.roles());
    return ResponseEntity.ok(
        new LoginHttpResponse(token.value(), "Bearer", token.expiresIn().toSeconds()));
  }

  private static boolean isInvalid(String value) {
    return value == null || value.isBlank() || value.length() > MAX_FIELD_LENGTH;
  }
}
