CREATE TABLE service_order_saga (
    service_order_id uuid PRIMARY KEY REFERENCES ordem_servico(id),
    state varchar(40) NOT NULL CHECK (state IN ('IN_DIAGNOSIS', 'WAITING_APPROVAL', 'MANUAL_INTERVENTION')),
    version bigint NOT NULL DEFAULT 0,
    reason varchar(100),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Only unquoted diagnosis orders can safely resume this first slice. Historical
-- decisions, execution and terminal orders require explicit reconciliation.
INSERT INTO service_order_saga(service_order_id, state, reason)
SELECT id, CASE WHEN status IN ('RECEIVED', 'IN_DIAGNOSIS') AND current_budget_id IS NULL
                    AND approved_budget_id IS NULL THEN 'IN_DIAGNOSIS' ELSE 'MANUAL_INTERVENTION' END,
       CASE WHEN status IN ('RECEIVED', 'IN_DIAGNOSIS') AND current_budget_id IS NULL
                    AND approved_budget_id IS NULL THEN NULL ELSE 'PHASE3_LEGACY_RECONCILIATION' END
FROM ordem_servico;

CREATE TABLE inbox_event (
    event_id uuid PRIMARY KEY,
    content_hash char(64) NOT NULL,
    event_type varchar(80) NOT NULL,
    service_order_id uuid NOT NULL REFERENCES ordem_servico(id),
    correlation_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    outcome varchar(40) NOT NULL CHECK (outcome IN ('PROCESSING', 'APPLIED', 'OBSOLETE', 'IGNORED'))
);
COMMENT ON TABLE inbox_event IS 'Retain identities for at least the 30-day automatic replay horizon; no automatic purge in phase 3';
