package com.coldguard.asset.domain;

/** The aggregate changed since the caller read it (optimistic locking). */
public class StaleVersionException extends RuntimeException {

  public StaleVersionException(String kind, Object id) {
    super(kind + " was modified concurrently: " + id);
  }
}
