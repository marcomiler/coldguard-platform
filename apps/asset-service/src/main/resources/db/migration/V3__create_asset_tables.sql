-- Asset bounded context (organization, site, asset, sensor and its lifecycle). Foreign keys stay
-- inside this schema; nothing references another service's tables.

CREATE TABLE organization (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_organization_name ON organization (lower(name));

CREATE TABLE site (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(120) NOT NULL,
    address         VARCHAR(250),
    created_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_site_org_name ON site (organization_id, lower(name));

CREATE TABLE asset (
    id          UUID         PRIMARY KEY,
    site_id     UUID         NOT NULL REFERENCES site (id),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    criticality VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL
);
CREATE INDEX ix_asset_site ON asset (site_id);

CREATE TABLE sensor (
    id                           UUID         PRIMARY KEY,
    serial_number                VARCHAR(80)  NOT NULL,
    model                        VARCHAR(80),
    measurement_unit             VARCHAR(20)  NOT NULL,
    asset_id                     UUID         NOT NULL REFERENCES asset (id),
    status                       VARCHAR(20)  NOT NULL,
    status_changed_at            TIMESTAMPTZ  NOT NULL,
    -- The latest calibration is denormalized here (same aggregate) so the expiry job and the
    -- transition guards need no per-sensor subquery.
    last_calibration_recorded_at TIMESTAMPTZ,
    last_calibration_valid_until TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ  NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL,
    version                      BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_sensor_serial ON sensor (lower(serial_number));
CREATE INDEX ix_sensor_asset ON sensor (asset_id);
CREATE INDEX ix_sensor_calibration_due ON sensor (last_calibration_valid_until)
    WHERE status IN ('ACTIVE', 'INACTIVE') AND last_calibration_valid_until IS NOT NULL;

CREATE TABLE operational_profile (
    sensor_id                   UUID          PRIMARY KEY REFERENCES sensor (id),
    min_temperature             NUMERIC(6, 2) NOT NULL,
    max_temperature             NUMERIC(6, 2) NOT NULL,
    unit                        VARCHAR(20)   NOT NULL,
    magnitude_medium_from       NUMERIC(6, 2) NOT NULL,
    magnitude_high_from         NUMERIC(6, 2) NOT NULL,
    magnitude_critical_from     NUMERIC(6, 2) NOT NULL,
    persistence_min_consecutive INT           NOT NULL,
    persistence_window_seconds  INT           NOT NULL,
    expected_interval_seconds   INT           NOT NULL,
    calibration_validity_seconds BIGINT,
    updated_at                  TIMESTAMPTZ   NOT NULL,
    updated_by                  VARCHAR(100)  NOT NULL,
    version                     BIGINT        NOT NULL,
    CONSTRAINT ck_profile_range CHECK (min_temperature < max_temperature),
    CONSTRAINT ck_profile_bands CHECK (
        0 < magnitude_medium_from
        AND magnitude_medium_from < magnitude_high_from
        AND magnitude_high_from < magnitude_critical_from),
    CONSTRAINT ck_profile_persistence CHECK (
        persistence_min_consecutive >= 1 AND persistence_window_seconds > 0),
    CONSTRAINT ck_profile_interval CHECK (expected_interval_seconds > 0)
);

CREATE TABLE calibration_record (
    id          UUID         PRIMARY KEY,
    sensor_id   UUID         NOT NULL REFERENCES sensor (id),
    kind        VARCHAR(20)  NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ  NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL,
    recorded_by VARCHAR(100) NOT NULL,
    reason      VARCHAR(500) NOT NULL
);
CREATE INDEX ix_calibration_sensor ON calibration_record (sensor_id, recorded_at DESC);

-- Append-only: rows are only ever inserted (the sensor lifecycle is never deleted).
CREATE TABLE sensor_assignment_history (
    id                UUID         PRIMARY KEY,
    sensor_id         UUID         NOT NULL REFERENCES sensor (id),
    asset_id          UUID         NOT NULL,
    previous_asset_id UUID,
    assigned_at       TIMESTAMPTZ  NOT NULL,
    assigned_by       VARCHAR(100) NOT NULL,
    reason            VARCHAR(500) NOT NULL
);
CREATE INDEX ix_assignment_sensor ON sensor_assignment_history (sensor_id, assigned_at DESC);

-- Administrative history of the sensor: the source of the sensor history query.
CREATE TABLE sensor_lifecycle_audit (
    id             UUID         PRIMARY KEY,
    sensor_id      UUID         NOT NULL REFERENCES sensor (id),
    action         VARCHAR(40)  NOT NULL,
    previous_value JSONB,
    new_value      JSONB,
    reason         VARCHAR(500),
    actor_type     VARCHAR(10)  NOT NULL,
    actor_id       VARCHAR(100) NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_lifecycle_sensor ON sensor_lifecycle_audit (sensor_id, occurred_at DESC, id DESC);
