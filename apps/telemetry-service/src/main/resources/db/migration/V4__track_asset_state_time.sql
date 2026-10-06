-- When Asset announced the last change applied to a sensor (status, asset or reporting interval).
-- Asset events are not delivered in order, so an older change must not undo a newer one.
ALTER TABLE sensor_condition ADD COLUMN asset_state_at TIMESTAMPTZ;

-- Connectivity checks only look at sensors that are expected to report.
DROP INDEX ix_condition_connectivity;
CREATE INDEX ix_condition_connectivity ON sensor_condition (last_reading_at)
    WHERE connectivity_lost_at IS NULL AND sensor_status = 'ACTIVE' AND expected_interval_seconds > 0;
