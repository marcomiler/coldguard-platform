package com.coldguard.asset.application;

import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.commons.security.Actor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The administrative history of a sensor, newest first, paginated by keyset. */
@Service
public class SensorHistoryService {

  private final SensorRepository sensors;
  private final SensorHistoryRepository history;
  private final PageRequestPolicy paging;

  public SensorHistoryService(
      SensorRepository sensors, SensorHistoryRepository history, PageRequestPolicy paging) {
    this.sensors = sensors;
    this.history = history;
    this.paging = paging;
  }

  @Transactional(readOnly = true)
  public CursorPage<SensorHistoryEntry> history(
      Actor actor, UUID sensorId, String cursor, int requestedSize) {
    AccessPolicy.requireAdministrator(actor);
    sensors.findById(sensorId).orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
    int size = paging.sizeFor(requestedSize);
    paging.validate(0, size);
    List<SensorHistoryEntry> rows =
        history.findPage(sensorId, HistoryCursor.decode(cursor), size + 1);
    boolean hasMore = rows.size() > size;
    List<SensorHistoryEntry> page = hasMore ? rows.subList(0, size) : rows;
    String next = hasMore ? HistoryCursor.after(page.get(page.size() - 1)).encode() : "";
    return new CursorPage<>(List.copyOf(page), next, hasMore);
  }
}
