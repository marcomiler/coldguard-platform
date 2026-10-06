package com.coldguard.commons.security;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

/**
 * Exposes the actor propagated by the Gateway ({@code x-actor-id}, {@code x-actor-roles}) via
 * {@link #ACTOR_CONTEXT_KEY} for the duration of the call.
 *
 * <p>The metadata is plain strings, so it is honoured <b>only</b> when the mTLS peer certificate
 * (already validated against the local CA during the handshake) has the {@code gateway} identity.
 * From any other client the identity headers are ignored and the call runs without an actor, so a
 * service holding a valid certificate cannot impersonate a user.
 */
public class ActorServerInterceptor implements ServerInterceptor {

  public static final Metadata.Key<String> ACTOR_ID_KEY =
      Metadata.Key.of("x-actor-id", Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> ACTOR_ROLES_KEY =
      Metadata.Key.of("x-actor-roles", Metadata.ASCII_STRING_MARSHALLER);

  public static final Context.Key<Actor> ACTOR_CONTEXT_KEY = Context.key("actor");

  static final String GATEWAY_IDENTITY = "gateway";

  private final Predicate<Attributes> trustedPeer;

  public ActorServerInterceptor() {
    this(ActorServerInterceptor::isGatewayPeer);
  }

  public ActorServerInterceptor(Predicate<Attributes> trustedPeer) {
    this.trustedPeer = trustedPeer;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Actor actor = trustedPeer.test(call.getAttributes()) ? parseActor(headers) : null;
    Context context = Context.current().withValue(ACTOR_CONTEXT_KEY, actor);
    return Contexts.interceptCall(context, call, headers, next);
  }

  private static Actor parseActor(Metadata headers) {
    String id = headers.get(ACTOR_ID_KEY);
    if (id == null || id.isBlank()) {
      return null;
    }
    return new Actor(id.strip(), parseRoles(headers.get(ACTOR_ROLES_KEY)));
  }

  private static Set<Role> parseRoles(String header) {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    if (header == null) {
      return roles;
    }
    for (String name : header.split(",")) {
      try {
        roles.add(Role.valueOf(name.strip()));
      } catch (IllegalArgumentException unknownRole) {
        // An unknown name grants nothing.
      }
    }
    return roles;
  }

  static boolean isGatewayPeer(Attributes attributes) {
    SSLSession session = attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
    if (session == null) {
      return false;
    }
    try {
      Certificate[] chain = session.getPeerCertificates();
      return chain.length > 0
          && chain[0] instanceof X509Certificate leaf
          && GATEWAY_IDENTITY.equals(commonName(leaf));
    } catch (SSLPeerUnverifiedException | InvalidNameException noClientCertificate) {
      return false;
    }
  }

  private static String commonName(X509Certificate certificate) throws InvalidNameException {
    for (Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
      if ("CN".equalsIgnoreCase(rdn.getType())) {
        return String.valueOf(rdn.getValue());
      }
    }
    return null;
  }
}
