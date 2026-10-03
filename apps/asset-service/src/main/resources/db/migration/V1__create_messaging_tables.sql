CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    event_version   INT          NOT NULL,
    routing_key     VARCHAR(120) NOT NULL,
    payload         JSONB        NOT NULL,
    headers         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ  NULL,
    parked_at       TIMESTAMPTZ  NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL
);

CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at)
    WHERE published_at IS NULL AND parked_at IS NULL;

CREATE INDEX ix_outbox_event_pending_aggregate ON outbox_event (aggregate_type, aggregate_id)
    WHERE published_at IS NULL AND parked_at IS NULL;

CREATE TABLE processed_message (
    message_id   UUID        NOT NULL,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
