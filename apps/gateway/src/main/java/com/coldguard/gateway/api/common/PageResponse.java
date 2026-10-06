package com.coldguard.gateway.api.common;

import java.util.List;

/** One page of an offset-paginated listing. */
public record PageResponse<T>(List<T> items, PageInfo page) {

  public record PageInfo(int page, int size, long totalElements, int totalPages) {}
}
