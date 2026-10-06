package com.coldguard.gateway.api;

import java.util.List;

public record UserPageHttpResponse(
    List<UserHttpResponse> users, int page, int size, long totalElements, int totalPages) {}
