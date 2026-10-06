package com.coldguard.asset.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.asset.application.AssetAccessDeniedException;
import com.coldguard.asset.application.CalibrationValidityNotConfiguredException;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.StaleVersionException;
import io.grpc.Status;
import io.grpc.StatusException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetGrpcExceptionHandlerTest {

  private final AssetGrpcExceptionHandler handler = new AssetGrpcExceptionHandler();

  private static String code(StatusException e) {
    return e.getTrailers() == null
        ? null
        : e.getTrailers().get(AssetGrpcExceptionHandler.ERROR_CODE);
  }

  @Test
  void businessExceptionsMapToTheStatusAndStableCodeOfTheContract() {
    var notFound =
        handler.handleException(new ResourceNotFoundException("Sensor", UUID.randomUUID()));
    assertThat(notFound.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(code(notFound)).isEqualTo("SENSOR_NOT_FOUND");

    var duplicate =
        handler.handleException(new AlreadyExistsException("SENSOR_SERIAL_DUPLICATED", "taken"));
    assertThat(duplicate.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
    assertThat(code(duplicate)).isEqualTo("SENSOR_SERIAL_DUPLICATED");

    var stale = handler.handleException(new StaleVersionException("Asset", "1"));
    assertThat(stale.getStatus().getCode()).isEqualTo(Status.Code.ABORTED);
    assertThat(code(stale)).isEqualTo("CONCURRENT_MODIFICATION");

    var unconfigured = handler.handleException(new CalibrationValidityNotConfiguredException());
    assertThat(unconfigured.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    assertThat(code(unconfigured)).isEqualTo("CALIBRATION_VALIDITY_NOT_CONFIGURED");
  }

  @Test
  void accessAndValidationErrorsCarryNoBusinessCode() {
    var denied =
        handler.handleException(new AssetAccessDeniedException("PLATFORM_ADMIN role required"));
    assertThat(denied.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(code(denied)).isNull();

    var invalid = handler.handleException(new IllegalArgumentException("name is required"));
    assertThat(invalid.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(invalid.getStatus().getDescription()).isEqualTo("name is required");
  }

  @Test
  void anUnexpectedErrorIsAGenericInternalWithoutLeakingDetails() {
    var internal =
        handler.handleException(new IllegalStateException("jdbc:postgresql://secret-host"));

    assertThat(internal.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    assertThat(internal.getStatus().getDescription()).isEqualTo("Unexpected error");
    assertThat(internal.getStatus().getCause()).isNull();
  }
}
