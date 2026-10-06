package com.coldguard.incident.identity.application;

/**
 * Outcome of a credential check. A rejection is a result, not an exception, so the failed attempt
 * is committed; it deliberately carries no reason (unknown user, wrong password, disabled and
 * locked are indistinguishable to the caller).
 */
public sealed interface AuthenticationResult {

  record Authenticated(AuthenticatedUser user) implements AuthenticationResult {}

  record Rejected() implements AuthenticationResult {}
}
