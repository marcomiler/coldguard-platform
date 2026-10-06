package com.coldguard.asset.application;

/** Validates paging input against the configured maximum; an unset size uses the default. */
public record PageRequestPolicy(int maxSize, int defaultSize) {

  public PageRequestPolicy {
    if (maxSize < 1 || defaultSize < 1 || defaultSize > maxSize) {
      throw new IllegalArgumentException("invalid page size configuration");
    }
  }

  /** Returns the size to use; {@code requestedSize} 0 means "not specified". */
  public int sizeFor(int requestedSize) {
    return requestedSize == 0 ? defaultSize : requestedSize;
  }

  public void validate(int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }
    if (size < 1 || size > maxSize) {
      throw new IllegalArgumentException("size must be between 1 and " + maxSize);
    }
  }
}
