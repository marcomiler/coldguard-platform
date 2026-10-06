package com.coldguard.telemetry.api;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.telemetry.application.ConnectivityQueryService;
import com.coldguard.telemetry.application.IncomingReading;
import com.coldguard.telemetry.application.IngestReadingsService;
import com.coldguard.telemetry.application.ReadingQueryService;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.IngestReadingsResponse;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusRequest;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusResponse;
import com.coldguard.telemetry.grpc.v1.ListReadingsRequest;
import com.coldguard.telemetry.grpc.v1.ListReadingsResponse;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.UUID;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint of Telemetry (contracts/grpc/telemetry/v1). It translates and delegates; business
 * errors propagate as exceptions and {@link TelemetryGrpcExceptionHandler} maps them.
 */
@GrpcService
public class TelemetryGrpcService extends TelemetryServiceGrpc.TelemetryServiceImplBase {

  private final IngestReadingsService ingestion;
  private final ReadingQueryService queries;
  private final ConnectivityQueryService connectivity;

  public TelemetryGrpcService(
      IngestReadingsService ingestion,
      ReadingQueryService queries,
      ConnectivityQueryService connectivity) {
    this.ingestion = ingestion;
    this.queries = queries;
    this.connectivity = connectivity;
  }

  private static Actor actor() {
    return ActorServerInterceptor.ACTOR_CONTEXT_KEY.get();
  }

  @Override
  public void ingestReadings(
      IngestReadingsRequest request, StreamObserver<IngestReadingsResponse> observer) {
    var results =
        ingestion.ingest(
            actor(),
            TelemetryGrpcMapper.toDomain(request.getSource()),
            request.getReadingsList().stream()
                .map(
                    reading ->
                        new IncomingReading(
                            reading.getReadingId(),
                            reading.getSensorId(),
                            reading.hasRecordedAt() ? instant(reading.getRecordedAt()) : null,
                            reading.getValue(),
                            reading.getUnit()))
                .toList());
    IngestReadingsResponse.Builder reply = IngestReadingsResponse.newBuilder();
    results.forEach(result -> reply.addResults(TelemetryGrpcMapper.toGrpc(result)));
    observer.onNext(reply.build());
    observer.onCompleted();
  }

  @Override
  public void listReadings(
      ListReadingsRequest request, StreamObserver<ListReadingsResponse> observer) {
    UUID sensorId;
    try {
      sensorId = UUID.fromString(request.getSensorId());
    } catch (IllegalArgumentException notAnId) {
      throw new IllegalArgumentException("sensor_id is not a valid id");
    }
    var page =
        queries.list(
            actor(),
            sensorId,
            request.hasFrom() ? instant(request.getFrom()) : null,
            request.hasTo() ? instant(request.getTo()) : null,
            request.getPage().getCursor(),
            request.getPage().getSize());
    ListReadingsResponse.Builder reply =
        ListReadingsResponse.newBuilder()
            .setPage(
                com.coldguard.common.grpc.v1.CursorPageInfo.newBuilder()
                    .setNextCursor(page.nextCursor())
                    .setHasMore(page.hasMore()));
    page.readings().forEach(reading -> reply.addReadings(TelemetryGrpcMapper.toGrpc(reading)));
    observer.onNext(reply.build());
    observer.onCompleted();
  }

  @Override
  public void listConnectivityStatus(
      ListConnectivityStatusRequest request,
      StreamObserver<ListConnectivityStatusResponse> observer) {
    var page =
        connectivity.list(
            actor(),
            request.getOnlyLost(),
            request.getPage().getPage(),
            request.getPage().getSize());
    ListConnectivityStatusResponse.Builder reply =
        ListConnectivityStatusResponse.newBuilder()
            .setPage(
                com.coldguard.common.grpc.v1.PageInfo.newBuilder()
                    .setPage(page.page())
                    .setSize(page.size())
                    .setTotalElements(page.totalElements())
                    .setTotalPages(page.totalPages()));
    page.items().forEach(condition -> reply.addStatuses(TelemetryGrpcMapper.toGrpc(condition)));
    observer.onNext(reply.build());
    observer.onCompleted();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
