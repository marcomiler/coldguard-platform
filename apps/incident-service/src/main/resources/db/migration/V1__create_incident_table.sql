CREATE TABLE incident.incident (
    id UUID PRIMARY KEY,
    asset_id VARCHAR(100) NOT NULL,
    sensor_id VARCHAR(100) NOT NULL,
    anomaly_type VARCHAR(100) NOT NULL,
    impact VARCHAR(20) NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_incident_open UNIQUE (asset_id, sensor_id, anomaly_type)
);
