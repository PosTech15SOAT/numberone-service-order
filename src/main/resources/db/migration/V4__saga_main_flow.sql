ALTER TABLE service_order_saga DROP CONSTRAINT service_order_saga_state_check;
ALTER TABLE service_order_saga ADD CHECK (state IN ('IN_DIAGNOSIS','WAITING_APPROVAL','WAITING_REVISION',
    'READY_FOR_EXECUTION','START_PENDING','IN_EXECUTION','WAITING_PAYMENT','READY_FOR_DELIVERY',
    'COMPLETED','COMPENSATING','COMPENSATED','MANUAL_INTERVENTION'));
ALTER TABLE service_order_saga ADD COLUMN cancellation_in_progress boolean NOT NULL DEFAULT false;
ALTER TABLE service_order_saga ADD COLUMN settlement_pending boolean NOT NULL DEFAULT false;
ALTER TABLE service_order_saga ADD COLUMN settlement_verified_at timestamptz;
-- Payment events can precede budget events. The projection is not delivery authority.
ALTER TABLE ordem_servico DROP CONSTRAINT ck_payment_requires_snapshot;
ALTER TABLE ordem_servico ADD CONSTRAINT ck_current_approval_value CHECK
    (approved_composition_version IS NULL OR (approved_amount > 0 AND approved_currency='BRL'));
CREATE TABLE saga_operation (
    operation_id uuid PRIMARY KEY,
    service_order_id uuid NOT NULL REFERENCES ordem_servico(id),
    item_id uuid NOT NULL REFERENCES item_ordem_servico(id),
    composition_version integer NOT NULL,
    budget_revision integer NOT NULL,
    attempt integer NOT NULL CHECK (attempt > 0),
    intent varchar(30) NOT NULL DEFAULT 'START_ITEM',
    state varchar(30) NOT NULL CHECK (state IN ('START_PENDING','AWAITING_RESULT','CONFIRMED','REJECTED')),
    request jsonb NOT NULL,
    result jsonb,
    reason varchar(100),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(item_id, attempt)
);
CREATE UNIQUE INDEX one_active_item_start ON saga_operation(item_id) WHERE state IN ('START_PENDING','AWAITING_RESULT');
ALTER TABLE item_ordem_servico ADD COLUMN start_operation_id uuid REFERENCES saga_operation(operation_id);
CREATE TABLE service_order_payment (
    payment_id uuid PRIMARY KEY,
    service_order_id uuid NOT NULL REFERENCES ordem_servico(id),
    payment_version integer NOT NULL CHECK(payment_version > 0),
    status varchar(20) NOT NULL CHECK(status IN ('PAID','REFUNDED')),
    occurred_at timestamptz NOT NULL
);
