ALTER TABLE incident.incident
    DROP CONSTRAINT ux_incident_open;

CREATE UNIQUE INDEX ux_incident_open_created
    ON incident.incident (asset_id, sensor_id, anomaly_type)
    WHERE status = 'CREATED';
