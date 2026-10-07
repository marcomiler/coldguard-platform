ALTER TABLE incident.incident
    ADD COLUMN acknowledged_at     TIMESTAMPTZ   NULL,
    ADD COLUMN acknowledged_by     VARCHAR(100)  NULL,
    ADD COLUMN last_escalated_at   TIMESTAMPTZ   NULL,
    ADD COLUMN escalation_count    INT           NOT NULL DEFAULT 0,
    ADD COLUMN closed_at           TIMESTAMPTZ   NULL,
    ADD COLUMN closed_by           VARCHAR(100)  NULL,
    ADD COLUMN cause               VARCHAR(500)  NULL,
    ADD COLUMN resolution_comment  VARCHAR(2000) NULL,
    ADD COLUMN ack_due_at          TIMESTAMPTZ   NULL,
    ADD COLUMN resolve_due_at      TIMESTAMPTZ   NULL,
    ADD COLUMN occurrence_count    INT           NOT NULL DEFAULT 1,
    ADD COLUMN last_occurrence_at  TIMESTAMPTZ   NULL,
    ADD COLUMN last_magnitude      VARCHAR(10)   NULL,
    ADD COLUMN persistent          BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN source_reading_id   UUID          NULL,
    ADD COLUMN version             BIGINT        NOT NULL DEFAULT 0;

-- NOT VALID: rows closed before the evidence was persisted are not checked.
ALTER TABLE incident.incident
    ADD CONSTRAINT ck_incident_closed_evidence
    CHECK (status <> 'CLOSED'
           OR (cause IS NOT NULL AND resolution_comment IS NOT NULL AND closed_at IS NOT NULL))
    NOT VALID;
