CREATE TABLE notification (
    id                      UUID PRIMARY KEY,
    notification_request_id UUID         NOT NULL,
    incident_id             UUID         NOT NULL,
    notification_type       VARCHAR(30)  NOT NULL,
    priority                VARCHAR(5)   NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    completed_at            TIMESTAMPTZ  NULL
);

CREATE UNIQUE INDEX ux_notification_request ON notification (notification_request_id);
CREATE INDEX ix_notification_incident ON notification (incident_id);

-- The recipient's address is deliberately not stored: only who was notified, never where.
CREATE TABLE notification_delivery (
    id                  UUID PRIMARY KEY,
    notification_id     UUID         NOT NULL REFERENCES notification (id),
    recipient_user_id   VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    attempts            INT          NOT NULL DEFAULT 0,
    last_error_category VARCHAR(30)  NULL,
    sent_at             TIMESTAMPTZ  NULL
);

CREATE UNIQUE INDEX ux_delivery_recipient
    ON notification_delivery (notification_id, recipient_user_id);
