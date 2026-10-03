package com.coldguard.commons.messaging;

/** Who caused an event: a user id, or {@code SYSTEM} plus the automated process name. */
public record EventActor(Type type, String id) {

  public enum Type {
    USER,
    SYSTEM
  }

  public static EventActor user(String id) {
    return new EventActor(Type.USER, id);
  }

  public static EventActor system(String processName) {
    return new EventActor(Type.SYSTEM, processName);
  }
}
