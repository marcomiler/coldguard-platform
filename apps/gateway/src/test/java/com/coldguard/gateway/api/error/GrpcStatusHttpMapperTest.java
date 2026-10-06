package com.coldguard.gateway.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.gateway.infrastructure.DownstreamCallException;
import io.grpc.Status;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class GrpcStatusHttpMapperTest {

  private static DownstreamCallException failure(
      Status.Code code, String errorCode, String description) {
    return new DownstreamCallException("asset-service", code, errorCode, description, null);
  }

  static Stream<Arguments> table() {
    return Stream.of(
        Arguments.of(Status.Code.INVALID_ARGUMENT, null, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"),
        Arguments.of(
            Status.Code.INVALID_ARGUMENT, "UNIT_MISMATCH", HttpStatus.BAD_REQUEST, "UNIT_MISMATCH"),
        Arguments.of(Status.Code.UNAUTHENTICATED, null, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"),
        Arguments.of(Status.Code.PERMISSION_DENIED, null, HttpStatus.FORBIDDEN, "FORBIDDEN"),
        Arguments.of(Status.Code.NOT_FOUND, null, HttpStatus.NOT_FOUND, "NOT_FOUND"),
        Arguments.of(
            Status.Code.NOT_FOUND, "SENSOR_NOT_FOUND", HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND"),
        Arguments.of(Status.Code.ALREADY_EXISTS, null, HttpStatus.CONFLICT, "ALREADY_EXISTS"),
        Arguments.of(
            Status.Code.ALREADY_EXISTS,
            "SENSOR_SERIAL_DUPLICATED",
            HttpStatus.CONFLICT,
            "SENSOR_SERIAL_DUPLICATED"),
        Arguments.of(
            Status.Code.FAILED_PRECONDITION, null, HttpStatus.CONFLICT, "FAILED_PRECONDITION"),
        Arguments.of(
            Status.Code.FAILED_PRECONDITION,
            "SENSOR_TRANSITION_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "SENSOR_TRANSITION_NOT_ALLOWED"),
        Arguments.of(Status.Code.ABORTED, null, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION"),
        Arguments.of(
            Status.Code.DEADLINE_EXCEEDED, null, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT"),
        Arguments.of(
            Status.Code.UNAVAILABLE, null, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE"),
        Arguments.of(Status.Code.INTERNAL, null, HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR"),
        Arguments.of(Status.Code.UNKNOWN, null, HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR"),
        Arguments.of(Status.Code.UNIMPLEMENTED, null, HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR"));
  }

  @ParameterizedTest(name = "{0} / {1} -> {2} {3}")
  @MethodSource("table")
  void everyGrpcStatusHasItsHttpAnswerAndCode(
      Status.Code grpc, String published, HttpStatus http, String code) {
    var mapped = GrpcStatusHttpMapper.map(failure(grpc, published, "a description"));

    assertThat(mapped.status()).isEqualTo(http);
    assertThat(mapped.code()).isEqualTo(code);
  }

  @Test
  void aBusinessDescriptionReachesTheClientButAnInternalOneNeverDoes() {
    assertThat(
            GrpcStatusHttpMapper.map(
                    failure(Status.Code.NOT_FOUND, "SENSOR_NOT_FOUND", "Sensor not found: x"))
                .detail())
        .isEqualTo("Sensor not found: x");
    assertThat(
            GrpcStatusHttpMapper.map(
                    failure(Status.Code.FAILED_PRECONDITION, "C", "The sensor is retired"))
                .detail())
        .isEqualTo("The sensor is retired");

    for (Status.Code internal :
        new Status.Code[] {
          Status.Code.INTERNAL,
          Status.Code.UNKNOWN,
          Status.Code.UNAVAILABLE,
          Status.Code.DEADLINE_EXCEEDED
        }) {
      var mapped =
          GrpcStatusHttpMapper.map(failure(internal, null, "jdbc:postgresql://secret-host"));
      assertThat(mapped.detail()).doesNotContain("secret").doesNotContain("jdbc");
    }
  }

  @Test
  void onlyServerSideFailuresAreLoggedAsErrors() {
    assertThat(GrpcStatusHttpMapper.map(failure(Status.Code.INTERNAL, null, "x")).logAsError())
        .isTrue();
    assertThat(GrpcStatusHttpMapper.map(failure(Status.Code.UNAVAILABLE, null, "x")).logAsError())
        .isTrue();
    assertThat(
            GrpcStatusHttpMapper.map(failure(Status.Code.DEADLINE_EXCEEDED, null, "x"))
                .logAsError())
        .isTrue();
    assertThat(GrpcStatusHttpMapper.map(failure(Status.Code.NOT_FOUND, null, "x")).logAsError())
        .isFalse();
    assertThat(GrpcStatusHttpMapper.map(failure(Status.Code.ABORTED, null, "x")).logAsError())
        .isFalse();
  }
}
