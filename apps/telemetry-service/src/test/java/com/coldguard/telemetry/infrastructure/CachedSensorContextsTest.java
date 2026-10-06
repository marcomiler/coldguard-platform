package com.coldguard.telemetry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.grpc.v1.SensorStatus;
import com.coldguard.telemetry.application.AssetUnavailableException;
import com.github.benmanes.caffeine.cache.Ticker;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachedSensorContextsTest {

  private final AtomicLong nanos = new AtomicLong();
  private final Ticker ticker = nanos::get;
  private FakeAsset asset;
  private CachedSensorContexts cache;

  @BeforeEach
  void start() throws Exception {
    asset = new FakeAsset();
    cache =
        new CachedSensorContexts(
            new AssetContextClient(asset.stub, Duration.ofSeconds(2)),
            Duration.ofSeconds(60),
            1000,
            ticker);
  }

  @AfterEach
  void stop() throws Exception {
    asset.close();
  }

  private UUID sensor() {
    return UUID.fromString(asset.addSensor(SensorStatus.SENSOR_STATUS_ACTIVE, true));
  }

  @Test
  void aSensorIsAskedFromAssetOnlyOnceWhileItsContextIsFresh() {
    UUID id = sensor();

    cache.resolve(List.of(id));
    var again = cache.resolve(List.of(id));

    assertThat(asset.calls).hasSize(1);
    assertThat(again).containsKey(id);
  }

  @Test
  void theContextExpiresAfterTheTimeToLive() {
    UUID id = sensor();
    cache.resolve(List.of(id));

    nanos.addAndGet(Duration.ofSeconds(59).toNanos());
    cache.resolve(List.of(id));
    assertThat(asset.calls).hasSize(1);

    nanos.addAndGet(Duration.ofSeconds(2).toNanos());
    cache.resolve(List.of(id));
    assertThat(asset.calls).hasSize(2);
  }

  @Test
  void onlyTheSensorsNotInTheCacheAreAsked() {
    UUID cached = sensor();
    UUID fresh = sensor();
    cache.resolve(List.of(cached));

    var resolved = cache.resolve(List.of(cached, fresh));

    assertThat(resolved).containsOnlyKeys(cached, fresh);
    assertThat(asset.calls).hasSize(2);
    assertThat(asset.calls.get(1)).containsExactly(fresh.toString());
  }

  @Test
  void aSensorThatDoesNotExistIsNeverCached() {
    UUID missing = UUID.randomUUID();

    assertThat(cache.resolve(List.of(missing))).isEmpty();
    assertThat(cache.resolve(List.of(missing))).isEmpty();

    assertThat(asset.calls).hasSize(2);
  }

  @Test
  void aCachedSensorIsServedEvenWhileAssetIsDown() {
    UUID cached = sensor();
    cache.resolve(List.of(cached));
    asset.failure = Status.UNAVAILABLE;

    assertThat(cache.resolve(List.of(cached))).containsKey(cached);
  }

  @Test
  void aSensorNotCachedWhileAssetIsDownMakesTheRequestFail() {
    UUID cached = sensor();
    UUID other = sensor();
    cache.resolve(List.of(cached));
    asset.failure = Status.UNAVAILABLE;

    assertThatThrownBy(() -> cache.resolve(List.of(cached, other)))
        .isInstanceOf(AssetUnavailableException.class);
  }

  @Test
  void evictingForgetsOneSensorAndEvictAllForgetsEverything() {
    UUID a = sensor();
    UUID b = sensor();
    cache.resolve(List.of(a, b));

    cache.evict(a);
    cache.resolve(List.of(a, b));
    assertThat(asset.calls).hasSize(2);
    assertThat(asset.calls.get(1)).containsExactly(a.toString());

    cache.evictAll();
    cache.resolve(List.of(a, b));
    assertThat(asset.calls).hasSize(3);
    assertThat(asset.calls.get(2)).containsExactlyInAnyOrder(a.toString(), b.toString());
  }
}
