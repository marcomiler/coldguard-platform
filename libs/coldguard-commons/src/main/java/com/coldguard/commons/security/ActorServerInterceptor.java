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
import java.util.function.Function;
import java.util.function.Predicate;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

/**
 * Exposes the caller of a gRPC call via {@link #ACTOR_CONTEXT_KEY} for the duration of the call.
 *
 * <p>Who the caller is comes from the mTLS peer certificate (already validated against the local CA
 * during the handshake), never from anything the caller merely claims:
 *
 * <ul>
 *   <li>The {@code gateway} identity may propagate a user as metadata ({@code x-actor-id}, {@code
 *       x-actor-roles}); the Gateway can never propagate a {@code system:} actor.
 *   <li>A peer whose common name is in the configured set of system callers (for example the
 *       telemetry service reading Asset data) becomes {@link Actor#system} named after its
 *       certificate; any identity metadata it sends is ignored.
 *   <li>Any other peer, or no certificate, runs without an actor, so a service holding a valid
 *       certificate cannot impersonate a user.
 * </ul>
 */
public class ActorServerInterceptor implements ServerInterceptor {

  public static final Metadata.Key<String> ACTOR_ID_KEY =
      Metadata.Key.of("x-actor-id", Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> ACTOR_ROLES_KEY =
      Metadata.Key.of("x-actor-roles", Metadata.ASCII_STRING_MARSHALLER);

  public static final Context.Key<Actor> ACTOR_CONTEXT_KEY = Context.key("actor");

  static final String GATEWAY_IDENTITY = "gateway";

  private static final String SYSTEM_PREFIX = "system:";

  private final Function<Attributes, String> peerName;
  private final Set<String> systemCallers;

  /** The Gateway is the only trusted peer; there are no system callers. */
  public ActorServerInterceptor() {
    this(Set.of());
  }

  /** The Gateway plus these certificate common names, which act as system callers. */
  public ActorServerInterceptor(Set<String> systemCallers) {
    this(ActorServerInterceptor::peerCommonName, systemCallers);
  }

  /** Test seam: every peer for which {@code trustedPeer} holds is treated as the Gateway. */
  public ActorServerInterceptor(Predicate<Attributes> trustedPeer) {
    this(attributes -> trustedPeer.test(attributes) ? GATEWAY_IDENTITY : null, Set.of());
  }

  public ActorServerInterceptor(Function<Attributes, String> peerName, Set<String> systemCallers) {
    this.peerName = peerName;
    this.systemCallers = Set.copyOf(systemCallers);
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Context context =
        Context.current().withValue(ACTOR_CONTEXT_KEY, actorOf(call.getAttributes(), headers));
    return Contexts.interceptCall(context, call, headers, next);
  }

  private Actor actorOf(Attributes attributes, Metadata headers) {
    String peer = peerName.apply(attributes);
    if (peer == null) {
      return null;
    }
    if (GATEWAY_IDENTITY.equals(peer)) {
      return parseActor(headers);
    }
    return systemCallers.contains(peer) ? Actor.system(peer) : null;
  }

  private static Actor parseActor(Metadata headers) {
    String id = headers.get(ACTOR_ID_KEY);
    if (id == null || id.isBlank() || id.strip().startsWith(SYSTEM_PREFIX)) {
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
    return GATEWAY_IDENTITY.equals(peerCommonName(attributes));
  }

  /** Common name of the validated client certificate, or null when there is none. */
  static String peerCommonName(Attributes attributes) {
    SSLSession session = attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
    if (session == null) {
      return null;
    }
    try {
      Certificate[] chain = session.getPeerCertificates();
      return chain.length > 0 && chain[0] instanceof X509Certificate leaf ? commonName(leaf) : null;
    } catch (SSLPeerUnverifiedException | InvalidNameException noClientCertificate) {
      return null;
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
