DROP INDEX incident.ux_incident_open_created;

CREATE UNIQUE INDEX ux_incident_open
    ON incident.incident (asset_id, sensor_id, anomaly_type)
    WHERE status <> 'CLOSED';

CREATE INDEX ix_incident_status_priority_created
    ON incident.incident (status, priority, created_at DESC);

CREATE INDEX ix_incident_created ON incident.incident (created_at);
