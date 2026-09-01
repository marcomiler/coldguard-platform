package com.coldguard.gateway.infrastructure;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Propagates the authenticated actor's role to Incident Service as gRPC metadata (never as a
 * request field). Applies to every outgoing call; only CloseIncident reads it today.
 *
 * <p>Only the exact authority Incident Service checks against (RN-019) is ever propagated —
 * never "the first" authority in the collection. Spring Security's resource server adds other
 * authorities (e.g. {@code FACTOR_BEARER}) to every JWT-authenticated principal, so picking the
 * first one is not a valid way to find the role. If the required authority is absent, no
 * metadata is sent at all; Incident Service's own null-check already fails the call closed with
 * {@code PERMISSION_DENIED} (see ActorRoleServerInterceptor) — this class does not duplicate
 * that rejection locally.
 */
@Component
@GlobalClientInterceptor
public class ActorRoleClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> ACTOR_ROLE_KEY =
            Metadata.Key.of("x-actor-role", Metadata.ASCII_STRING_MARSHALLER);

    static final String REQUIRED_ROLE_AUTHORITY = "ROLE_MAINTENANCE_TECHNICIAN";

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                findRequiredRoleAuthority(SecurityContextHolder.getContext().getAuthentication())
                        .ifPresent(authority -> headers.put(ACTOR_ROLE_KEY, authority));
                super.start(responseListener, headers);
            }
        };
    }

    private static Optional<String> findRequiredRoleAuthority(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(REQUIRED_ROLE_AUTHORITY::equals)
                .findFirst();
    }
}
