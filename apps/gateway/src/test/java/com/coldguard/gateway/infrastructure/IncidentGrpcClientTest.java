package com.coldguard.gateway.infrastructure;

import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.Criticality;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentStatus;
import com.coldguard.incident.grpc.v1.Magnitude;
import com.coldguard.incident.grpc.v1.Priority;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.Server;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link IncidentGrpcClient} against an in-process gRPC server backed by a
 * scriptable test double of IncidentService, without a real network transport.
 */
class IncidentGrpcClientTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void createIncident_success_returnsResponse() throws Exception {
        CreateIncidentResponse canned = CreateIncidentResponse.newBuilder()
                .setIncidentId("incident-1")
                .setStatus(IncidentStatus.CREATED)
                .setPriority(Priority.P1)
                .build();
        AtomicReference<CreateIncidentRequest> receivedRequest = new AtomicReference<>();
        IncidentGrpcClient client = startClient(new IncidentServiceGrpc.IncidentServiceImplBase() {
            @Override
            public void createIncident(CreateIncidentRequest request,
                                        StreamObserver<CreateIncidentResponse> responseObserver) {
                receivedRequest.set(request);
                responseObserver.onNext(canned);
                responseObserver.onCompleted();
            }
        });

        CreateIncidentResponse response = client.createIncident(validRequest());

        assertThat(response.getIncidentId()).isEqualTo("incident-1");
        assertThat(response.getPriority()).isEqualTo(Priority.P1);
        assertThat(receivedRequest.get().getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void createIncident_alreadyExists_throwsIncidentAlreadyExistsException() throws Exception {
        IncidentGrpcClient client = startClient(failingWith(
                Status.ALREADY_EXISTS.withDescription("duplicate incident")));

        assertThatThrownBy(() -> client.createIncident(validRequest()))
                .isInstanceOf(IncidentAlreadyExistsException.class)
                .hasMessageContaining("duplicate incident");
    }

    @Test
    void createIncident_invalidArgument_throwsInvalidIncidentRequestException() throws Exception {
        IncidentGrpcClient client = startClient(failingWith(
                Status.INVALID_ARGUMENT.withDescription("magnitude is required")));

        assertThatThrownBy(() -> client.createIncident(validRequest()))
                .isInstanceOf(InvalidIncidentRequestException.class)
                .hasMessageContaining("magnitude is required");
    }

    @Test
    void createIncident_internalError_throwsIncidentServiceException() throws Exception {
        IncidentGrpcClient client = startClient(failingWith(
                Status.INTERNAL.withDescription("unexpected error")));

        assertThatThrownBy(() -> client.createIncident(validRequest()))
                .isInstanceOf(IncidentServiceException.class)
                .isNotInstanceOf(IncidentAlreadyExistsException.class)
                .isNotInstanceOf(InvalidIncidentRequestException.class);
    }

    private IncidentServiceGrpc.IncidentServiceImplBase failingWith(Status status) {
        return new IncidentServiceGrpc.IncidentServiceImplBase() {
            @Override
            public void createIncident(CreateIncidentRequest request,
                                        StreamObserver<CreateIncidentResponse> responseObserver) {
                responseObserver.onError(status.asRuntimeException());
            }
        };
    }

    private IncidentGrpcClient startClient(IncidentServiceGrpc.IncidentServiceImplBase service) throws Exception {
        String serverName = "incident-grpc-client-test-" + System.nanoTime();
        server = InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        return new IncidentGrpcClient(IncidentServiceGrpc.newBlockingStub(channel));
    }

    private static CreateIncidentRequest validRequest() {
        return CreateIncidentRequest.newBuilder()
                .setAssetId("asset-1")
                .setAssetCriticality(Criticality.CRITICALITY_HIGH)
                .setSensorId("sensor-1")
                .setAnomalyType("high-temperature")
                .setMagnitude(Magnitude.MAGNITUDE_HIGH)
                .setPersistent(false)
                .setCorrelationId("corr-1")
                .build();
    }
}
