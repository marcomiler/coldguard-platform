package com.coldguard.asset.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.security.Actor;

/** Maps the caller to who is recorded in events and history. */
final class Actors {

  private static final String SYSTEM_PREFIX = "system:";

  private Actors() {}

  static EventActor toEventActor(Actor actor) {
    return isSystem(actor)
        ? EventActor.system(actor.id().substring(SYSTEM_PREFIX.length()))
        : EventActor.user(actor.id());
  }

  /** The id recorded in history: the user id, or the bare process name of a system actor. */
  static String id(Actor actor) {
    return isSystem(actor) ? actor.id().substring(SYSTEM_PREFIX.length()) : actor.id();
  }

  static String type(Actor actor) {
    return isSystem(actor) ? "SYSTEM" : "USER";
  }

  private static boolean isSystem(Actor actor) {
    return actor.id().startsWith(SYSTEM_PREFIX);
  }
}
