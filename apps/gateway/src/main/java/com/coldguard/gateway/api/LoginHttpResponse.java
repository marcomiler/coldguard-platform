package com.coldguard.gateway.api;

public record LoginHttpResponse(String accessToken, String tokenType, long expiresIn) {}
