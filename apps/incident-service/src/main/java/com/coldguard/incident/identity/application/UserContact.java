package com.coldguard.incident.identity.application;

/** The minimum needed to reach a user: never carries credentials. */
public record UserContact(String userId, String email) {}
