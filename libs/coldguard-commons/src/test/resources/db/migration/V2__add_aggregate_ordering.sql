ALTER TABLE outbox_event ADD COLUMN aggregate_version BIGINT NULL;

CREATE TABLE aggregate_sequence (
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    last_version   BIGINT       NOT NULL,
    PRIMARY KEY (aggregate_type, aggregate_id)
);

CREATE TABLE aggregate_cursor (
    consumer       VARCHAR(80)  NOT NULL,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    last_version   BIGINT       NOT NULL,
    PRIMARY KEY (consumer, aggregate_type, aggregate_id)
);
