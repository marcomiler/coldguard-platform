package com.coldguard.incident.application;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

  public int totalPages() {
    return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
  }
}
