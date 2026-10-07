CREATE TABLE auditlog.audit_record (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    recorded_at    TIMESTAMPTZ  NOT NULL,
    source_service VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      VARCHAR(100) NOT NULL,
    action         VARCHAR(60)  NOT NULL,
    actor_type     VARCHAR(10)  NOT NULL,
    actor_id       VARCHAR(100) NOT NULL,
    reason         VARCHAR(2000) NULL,
    previous_value JSONB        NULL,
    new_value      JSONB        NULL,
    correlation_id VARCHAR(100) NULL,
    source_event_id UUID        NULL
);

CREATE UNIQUE INDEX ux_audit_source_event
    ON auditlog.audit_record (source_event_id) WHERE source_event_id IS NOT NULL;
CREATE INDEX ix_audit_entity ON auditlog.audit_record (entity_type, entity_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_actor  ON auditlog.audit_record (actor_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_time   ON auditlog.audit_record (occurred_at DESC, id DESC);
