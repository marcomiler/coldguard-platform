package com.coldguard.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

class NoiseFreeObservationsTest {

  private final NoiseFreeObservations predicate = new NoiseFreeObservations();

  static class OutboxRelayLike {
    public void run() {}
  }

  static class ConnectivityJobLike {
    public void run() {}
  }

  private static ServerRequestObservationContext http(String path) {
    return new ServerRequestObservationContext(
        new MockHttpServletRequest("GET", path), new MockHttpServletResponse());
  }

  private static ScheduledTaskObservationContext task(Object target) throws Exception {
    Method run = target.getClass().getMethod("run");
    return new ScheduledTaskObservationContext(target, run);
  }

  @Test
  void actuatorRequestsAreDroppedAndBusinessRequestsKept() {
    assertThat(predicate.test("http.server.requests", http("/actuator/prometheus"))).isFalse();
    assertThat(predicate.test("http.server.requests", http("/actuator/health/readiness")))
        .isFalse();
    assertThat(predicate.test("http.server.requests", http("/api/v1/incidents"))).isTrue();
  }

  @Test
  void theOutboxRelayAndHousekeepingAreDroppedButOtherTasksAreKept() throws Exception {
    assertThat(predicate.test("tasks.scheduled.execute", task(new OutboxRelayLike()))).isFalse();
    assertThat(predicate.test("tasks.scheduled.execute", task(new ConnectivityJobLike()))).isTrue();
  }

  @Test
  void everythingElseIsKept() {
    assertThat(predicate.test("grpc.server", new Observation.Context())).isTrue();
  }
}
