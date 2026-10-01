package com.coldguard.incident.api;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Reads the actor role propagated by the Gateway as gRPC metadata (not as a request field) and
 * exposes it via {@link #ACTOR_ROLE_CONTEXT_KEY} for the duration of the call.
 *
 * <p><b>This is not cryptographic authentication.</b> The {@code x-actor-role} value is a plain
 * string with no signature, no mTLS, and no shared secret tying it to the Gateway's JWT validation
 * — anything that can reach this service's gRPC port directly (e.g. another container on the same
 * network, or a manual {@code grpcurl} call) can set this header itself and impersonate any role.
 * The actual security boundary is the Gateway's own JWT/RBAC check before it ever calls this
 * service; this interceptor only provides defense-in-depth consistency once that boundary holds. It
 * is only as safe as the network path between Gateway and Incident Service — that isolation is not
 * implemented in {@code deploy/local/docker-compose.yml} today (all services share the same
 * network) and must be addressed before this is relied on outside local development.
 */
@Component
@GlobalServerInterceptor
public class ActorRoleServerInterceptor implements ServerInterceptor {

  static final Metadata.Key<String> ACTOR_ROLE_KEY =
      Metadata.Key.of("x-actor-role", Metadata.ASCII_STRING_MARSHALLER);

  static final Context.Key<String> ACTOR_ROLE_CONTEXT_KEY = Context.key("actorRole");

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Context context =
        Context.current().withValue(ACTOR_ROLE_CONTEXT_KEY, headers.get(ACTOR_ROLE_KEY));
    return Contexts.interceptCall(context, call, headers, next);
  }
}
