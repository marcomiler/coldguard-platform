-- Telemetry bounded context. sensor_id and asset_id are logical references to the Asset service
-- (no foreign keys across schemas).

CREATE TABLE telemetry_reading (
    id                   UUID          PRIMARY KEY,        -- the producer's reading id: idempotency
    sensor_id            UUID          NOT NULL,
    asset_id             UUID          NOT NULL,
    recorded_at          TIMESTAMPTZ   NOT NULL,
    received_at          TIMESTAMPTZ   NOT NULL,
    value                NUMERIC(8, 3) NOT NULL,
    unit                 VARCHAR(20)   NOT NULL,
    source               VARCHAR(20)   NOT NULL,           -- SIMULATOR | TEST_INJECTION
    eligible             BOOLEAN       NOT NULL,
    ineligibility_reason VARCHAR(30),                      -- SENSOR_NOT_ACTIVE | NO_PROFILE
    breached             BOOLEAN       NOT NULL,
    anomaly_type         VARCHAR(30),
    magnitude            VARCHAR(10),
    correlation_id       VARCHAR(100)
);
CREATE INDEX ix_reading_sensor_time ON telemetry_reading (sensor_id, recorded_at DESC, id DESC);

-- Local read model of what the evaluation needs between readings (streak, connectivity); it is not
-- a copy of the Asset sensor aggregate.
CREATE TABLE sensor_condition (
    sensor_id                 UUID        PRIMARY KEY,
    asset_id                  UUID        NOT NULL,
    sensor_status             VARCHAR(20) NOT NULL,
    expected_interval_seconds INT         NOT NULL,
    last_reading_at           TIMESTAMPTZ NOT NULL,
    last_evaluated_recorded_at TIMESTAMPTZ,
    breach_anomaly_type       VARCHAR(30),
    breach_streak             INT         NOT NULL DEFAULT 0,
    breach_streak_started_at  TIMESTAMPTZ,
    connectivity_lost_at      TIMESTAMPTZ,
    version                   BIGINT      NOT NULL
);
CREATE INDEX ix_condition_connectivity ON sensor_condition (last_reading_at)
    WHERE connectivity_lost_at IS NULL AND sensor_status = 'ACTIVE';
