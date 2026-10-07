package com.coldguard.gateway.api.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
