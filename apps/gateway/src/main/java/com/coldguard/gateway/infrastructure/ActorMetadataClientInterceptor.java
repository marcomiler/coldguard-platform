package com.coldguard.gateway.infrastructure;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.stream.Collectors;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Propagates the authenticated actor to internal services as gRPC metadata (never as a request
 * field): {@code x-actor-id} (the JWT subject) and {@code x-actor-roles} (the role names without
 * the {@code ROLE_} prefix, comma-separated). Applies to every outgoing call.
 *
 * <p>Only a validated JWT authentication is propagated, and only its {@code ROLE_} authorities —
 * Spring Security adds others (e.g. {@code FACTOR_BEARER}) that are not roles. Without a JWT
 * authentication nothing is sent; internal services then treat the call as having no actor.
 */
@Component
@GlobalClientInterceptor
public class ActorMetadataClientInterceptor implements ClientInterceptor {

  static final Metadata.Key<String> ACTOR_ID_KEY =
      Metadata.Key.of("x-actor-id", Metadata.ASCII_STRING_MARSHALLER);

  static final Metadata.Key<String> ACTOR_ROLES_KEY =
      Metadata.Key.of("x-actor-roles", Metadata.ASCII_STRING_MARSHALLER);

  private static final String ROLE_PREFIX = "ROLE_";

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        if (SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken jwt) {
          String subject = jwt.getToken().getSubject();
          if (subject != null && !subject.isBlank()) {
            headers.put(ACTOR_ID_KEY, subject);
            headers.put(
                ACTOR_ROLES_KEY,
                jwt.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith(ROLE_PREFIX))
                    .map(authority -> authority.substring(ROLE_PREFIX.length()))
                    .collect(Collectors.joining(",")));
          }
        }
        super.start(responseListener, headers);
      }
    };
  }
}
