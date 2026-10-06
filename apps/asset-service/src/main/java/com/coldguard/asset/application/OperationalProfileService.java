package com.coldguard.asset.application;

import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The per-sensor operational profile that Telemetry evaluates readings against. */
@Service
public class OperationalProfileService {

  private final SensorRepository sensors;
  private final OperationalProfileRepository profiles;
  private final SensorHistoryRepository history;
  private final DomainEventPublisher events;
  private final Clock clock;

  public OperationalProfileService(
      SensorRepository sensors,
      OperationalProfileRepository profiles,
      SensorHistoryRepository history,
      DomainEventPublisher events,
      Clock clock) {
    this.sensors = sensors;
    this.profiles = profiles;
    this.history = history;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Creates the profile ({@code expectedVersion} 0) or updates it. Saving a profile identical to
   * the stored one changes nothing, publishes nothing and keeps its version.
   *
   * @throws com.coldguard.asset.domain.StaleVersionException on a version mismatch
   */
  @Transactional
  public OperationalProfile upsert(
      Actor actor, OperationalProfileDraft draft, long expectedVersion) {
    AccessPolicy.requireAdministrator(actor);
    Sensor sensor =
        sensors
            .findById(draft.sensorId())
            .orElseThrow(() -> new ResourceNotFoundException("Sensor", draft.sensorId()));
    return apply(actor, sensor, draft, expectedVersion, clock.instant());
  }

  /** Same as {@link #upsert} for a sensor the caller already loaded, inside its transaction. */
  OperationalProfile apply(
      Actor actor,
      Sensor sensor,
      OperationalProfileDraft draft,
      long expectedVersion,
      Instant now) {
    OperationalProfile candidate = draft.toProfile(sensor, actor.id(), now);
    OperationalProfile previous = profiles.findBySensorId(sensor.id()).orElse(null);
    if (previous != null && previous.version() != expectedVersion) {
      throw new com.coldguard.asset.domain.StaleVersionException("OperationalProfile", sensor.id());
    }
    if (previous == null && expectedVersion != 0) {
      throw new com.coldguard.asset.domain.StaleVersionException("OperationalProfile", sensor.id());
    }
    if (previous != null && previous.snapshot().equals(candidate.snapshot())) {
      return previous;
    }
    OperationalProfile saved = profiles.save(candidate, expectedVersion);
    events.publish(
        AssetEvents.operationalProfileUpdated(previous, saved, Actors.toEventActor(actor), now));
    history.addEntry(
        new SensorHistoryEntry(
            UUID.randomUUID(),
            sensor.id(),
            "PROFILE_UPDATED",
            previous == null ? null : previous.snapshot(),
            saved.snapshot(),
            null,
            Actors.type(actor),
            actor.id(),
            now));
    return saved;
  }

  @Transactional(readOnly = true)
  public OperationalProfile get(Actor actor, UUID sensorId) {
    AccessPolicy.requireReader(actor);
    return profiles
        .findBySensorId(sensorId)
        .orElseThrow(() -> new ResourceNotFoundException("OperationalProfile", sensorId));
  }
}
