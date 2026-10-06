package com.coldguard.gateway.api.asset;

import com.coldguard.asset.grpc.v1.GetOperationalProfileRequest;
import com.coldguard.asset.grpc.v1.GetSensorRequest;
import com.coldguard.gateway.infrastructure.AssetGrpcClient;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sensors, their operational profile and their lifecycle (status, calibrations, reassignment,
 * retirement, history). Business actions are sub-resources with POST. Shape validation, mapping and
 * the call; the transition rules live in the Asset service.
 */
@RestController
@RequestMapping("/api/v1/sensors")
class SensorController {

  private final AssetGrpcClient asset;

  SensorController(AssetGrpcClient asset) {
    this.asset = asset;
  }

  @GetMapping
  PageResponse<SensorResponse> list(
      @RequestParam(required = false) String assetId,
      @RequestParam(required = false) SensorStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int size) {
    var reply = asset.listSensors(AssetRestMapper.listSensors(assetId, status, page, size));
    return new PageResponse<>(
        reply.getSensorsList().stream().map(AssetRestMapper::toRest).toList(),
        AssetRestMapper.toRest(reply.getPage()));
  }

  @PostMapping
  ResponseEntity<SensorResponse> register(@Valid @RequestBody RegisterSensorRequest request) {
    SensorResponse created =
        AssetRestMapper.toRest(asset.registerSensor(AssetRestMapper.sensor(request)));
    return ResponseEntity.created(URI.create("/api/v1/sensors/" + created.id())).body(created);
  }

  @GetMapping("/{sensorId}")
  SensorResponse get(@PathVariable String sensorId) {
    return AssetRestMapper.toRest(
        asset.getSensor(GetSensorRequest.newBuilder().setSensorId(sensorId).build()));
  }

  @PatchMapping("/{sensorId}")
  SensorResponse update(
      @PathVariable String sensorId, @Valid @RequestBody UpdateSensorRequest request) {
    return AssetRestMapper.toRest(
        asset.updateSensor(AssetRestMapper.updateSensor(sensorId, request)));
  }

  @GetMapping("/{sensorId}/profile")
  OperationalProfileResponse getProfile(@PathVariable String sensorId) {
    return AssetRestMapper.toRest(
        asset.getOperationalProfile(
            GetOperationalProfileRequest.newBuilder().setSensorId(sensorId).build()));
  }

  @PutMapping("/{sensorId}/profile")
  OperationalProfileResponse putProfile(
      @PathVariable String sensorId, @Valid @RequestBody OperationalProfileRequest request) {
    return AssetRestMapper.toRest(
        asset.upsertOperationalProfile(AssetRestMapper.upsertProfile(sensorId, request)));
  }

  @PostMapping("/{sensorId}/status")
  SensorResponse changeStatus(
      @PathVariable String sensorId, @Valid @RequestBody StatusChangeRequest request) {
    return AssetRestMapper.toRest(
        asset.changeSensorStatus(AssetRestMapper.changeStatus(sensorId, request)));
  }

  /** Recording a calibration never changes the sensor's status. */
  @PostMapping("/{sensorId}/calibrations")
  ResponseEntity<CalibrationResponse> recordCalibration(
      @PathVariable String sensorId, @Valid @RequestBody CalibrationRequest request) {
    return ResponseEntity.status(201)
        .body(
            AssetRestMapper.toRest(
                asset.recordCalibration(AssetRestMapper.calibration(sensorId, request))));
  }

  @PostMapping("/{sensorId}/reassignment")
  SensorResponse reassign(
      @PathVariable String sensorId, @Valid @RequestBody ReassignmentRequest request) {
    return AssetRestMapper.toRest(
        asset.reassignSensor(AssetRestMapper.reassign(sensorId, request)));
  }

  @PostMapping("/{sensorId}/retirement")
  SensorResponse retire(
      @PathVariable String sensorId, @Valid @RequestBody RetirementRequest request) {
    return AssetRestMapper.toRest(asset.retireSensor(AssetRestMapper.retire(sensorId, request)));
  }

  @GetMapping("/{sensorId}/history")
  CursorPageResponse<HistoryEntryResponse> history(
      @PathVariable String sensorId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    var reply = asset.getSensorHistory(AssetRestMapper.history(sensorId, cursor, size));
    return new CursorPageResponse<>(
        reply.getEntriesList().stream().map(AssetRestMapper::toRest).toList(),
        reply.getPage().getNextCursor(),
        reply.getPage().getHasMore());
  }
}
