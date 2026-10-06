package com.coldguard.gateway.api.telemetry;

import com.coldguard.gateway.api.common.CursorPageResponse;
import com.coldguard.gateway.api.common.PageResponse;
import com.coldguard.gateway.infrastructure.TelemetryGrpcClient;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telemetry: controlled injection of test readings (through the same evaluation path as the
 * simulator) and reading back what a sensor reported. Shape validation, mapping and the call; the
 * evaluation lives in the Telemetry service.
 */
@RestController
@RequestMapping("/api/v1")
class TelemetryController {

  private final TelemetryGrpcClient telemetry;

  TelemetryController(TelemetryGrpcClient telemetry) {
    this.telemetry = telemetry;
  }

  /** Always 200: a batch is processed reading by reading and each one reports its own outcome. */
  @PostMapping("/telemetry/test-readings")
  TestReadingsResponse inject(@Valid @RequestBody TestReadingsRequest request) {
    var reply = telemetry.ingestReadings(TelemetryRestMapper.injection(request, Instant.now()));
    return new TestReadingsResponse(
        reply.getResultsList().stream().map(TelemetryRestMapper::toRest).toList());
  }

  @GetMapping("/sensors/{sensorId}/readings")
  CursorPageResponse<ReadingResponse> readings(
      @PathVariable String sensorId,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    var reply = telemetry.listReadings(TelemetryRestMapper.list(sensorId, from, to, cursor, size));
    return new CursorPageResponse<>(
        reply.getReadingsList().stream().map(TelemetryRestMapper::toRest).toList(),
        reply.getPage().getNextCursor(),
        reply.getPage().getHasMore());
  }

  /**
   * A literal route, so it takes precedence over {@code /sensors/{sensorId}} (which belongs to the
   * Asset resource).
   */
  @GetMapping("/sensors/connectivity")
  PageResponse<ConnectivityResponse> connectivity(
      @RequestParam(defaultValue = "false") boolean onlyLost,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int size) {
    var reply =
        telemetry.listConnectivityStatus(TelemetryRestMapper.connectivity(onlyLost, page, size));
    return new PageResponse<>(
        reply.getStatusesList().stream().map(TelemetryRestMapper::toRest).toList(),
        TelemetryRestMapper.toRest(reply.getPage()));
  }
}
