package com.coldguard.asset.application;

import java.util.List;

/** One zero-based page of results plus the total number of matches. */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

  public int totalPages() {
    return (int) ((totalElements + size - 1) / size);
  }
}
