package com.coldguard.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.incident.application.CloseIncidentService;
import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.Criticality;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.Magnitude;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;

/**
 * Runs {@link IncidentGrpcService} behind the same Spring gRPC exception-handler interceptor and
 * actor-role interceptor used at runtime, over a real in-process transport. Unlike the plain unit
 * test, nothing here maps exceptions by hand, so it proves the service can safely let exceptions
 * propagate.
 */
class IncidentGrpcServerWiringTest {

  private IncidentRepository repository;
  private boolean trustedPeer = true;
  private Server server;
  private ManagedChannel channel;

  @BeforeEach
  void startServer() throws Exception {
    repository = mock(IncidentRepository.class);
    IncidentGrpcService service =
        new IncidentGrpcService(
            new CreateIncidentService(repository), new CloseIncidentService(repository));
    String name = "incident-wiring-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .addService(
                ServerInterceptors.intercept(
                    service,
                    new ActorServerInterceptor(attributes -> trustedPeer),
                    new GrpcExceptionHandlerInterceptor(new IncidentGrpcExceptionHandler())))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).build();
  }

  @AfterEach
  void stop() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  private IncidentServiceGrpc.IncidentServiceBlockingStub stub(String actorRoles) {
    ClientInterceptor roleHeader =
        new ClientInterceptor() {
          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, options)) {
              @Override
              public void start(Listener<RespT> listener, Metadata headers) {
                if (actorRoles != null) {
                  headers.put(ActorServerInterceptor.ACTOR_ID_KEY, "user-1");
                  headers.put(ActorServerInterceptor.ACTOR_ROLES_KEY, actorRoles);
                }
                super.start(listener, headers);
              }
            };
          }
        };
    return IncidentServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, roleHeader));
  }

  private static CreateIncidentRequest.Builder validCreate() {
    return CreateIncidentRequest.newBuilder()
        .setAssetId("a1")
        .setAssetCriticality(Criticality.CRITICALITY_HIGH)
        .setSensorId("s1")
        .setAnomalyType("high-temperature")
        .setMagnitude(Magnitude.MAGNITUDE_HIGH);
  }

  private static Status.Code codeOf(StatusRuntimeException ex) {
    return ex.getStatus().getCode();
  }

  @Test
  void create_missingCriticality_mapsToInvalidArgumentWithoutTouchingTheRepository() {
    CreateIncidentRequest request =
        validCreate().setAssetCriticality(Criticality.CRITICALITY_UNSPECIFIED).build();

    assertThatThrownBy(() -> stub(null).createIncident(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> {
              assertThat(codeOf(ex)).isEqualTo(Status.Code.INVALID_ARGUMENT);
              assertThat(ex.getStatus().getDescription()).contains("asset_criticality");
            });
    verify(repository, never()).save(any());
  }

  @Test
  void create_openIncidentExists_mapsToAlreadyExistsWithExistingIdTrailer() {
    given(repository.findOpenIncidentId("a1", "s1", "high-temperature"))
        .willReturn(Optional.of("existing-1"));

    assertThatThrownBy(() -> stub(null).createIncident(validCreate().build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> {
              assertThat(codeOf(ex)).isEqualTo(Status.Code.ALREADY_EXISTS);
              assertThat(
                      ex.getTrailers().get(IncidentGrpcExceptionHandler.EXISTING_INCIDENT_ID_KEY))
                  .isEqualTo("existing-1");
            });
  }

  @Test
  void create_valid_returnsCalculatedIncident() {
    given(repository.findOpenIncidentId(any(), any(), any())).willReturn(Optional.empty());

    var response = stub(null).createIncident(validCreate().build());

    assertThat(response.getIncidentId()).isNotBlank();
    assertThat(response.getPriority().name()).isEqualTo("P2");
    verify(repository).save(any());
  }

  @Test
  void close_identityFromUntrustedPeer_isIgnoredAndMapsToPermissionDenied() {
    trustedPeer = false;
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId("i1")
            .setCause("c")
            .setResolutionComment("r")
            .build();

    assertThatThrownBy(() -> stub("MAINTENANCE_TECHNICIAN").closeIncident(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> assertThat(codeOf(ex)).isEqualTo(Status.Code.PERMISSION_DENIED));
  }

  @Test
  void close_withoutActorRoleMetadata_mapsToPermissionDenied() {
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId("i1")
            .setCause("c")
            .setResolutionComment("r")
            .build();

    assertThatThrownBy(() -> stub(null).closeIncident(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> assertThat(codeOf(ex)).isEqualTo(Status.Code.PERMISSION_DENIED));
  }

  @Test
  void close_authorizedRoleUnknownIncident_mapsToNotFound() {
    given(repository.findById("missing")).willReturn(Optional.empty());
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId("missing")
            .setCause("c")
            .setResolutionComment("r")
            .build();

    assertThatThrownBy(() -> stub("MAINTENANCE_TECHNICIAN").closeIncident(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> assertThat(codeOf(ex)).isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void close_blankCause_mapsToInvalidArgument() {
    CloseIncidentRequest request =
        CloseIncidentRequest.newBuilder()
            .setIncidentId("i1")
            .setCause(" ")
            .setResolutionComment("r")
            .build();

    assertThatThrownBy(() -> stub("MAINTENANCE_TECHNICIAN").closeIncident(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> assertThat(codeOf(ex)).isEqualTo(Status.Code.INVALID_ARGUMENT));
  }
}
