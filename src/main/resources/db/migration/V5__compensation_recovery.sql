ALTER TABLE service_order_saga ADD COLUMN cancellation jsonb;
ALTER TABLE service_order_saga ADD COLUMN review_decisions jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE service_order_saga ADD COLUMN return_state varchar(40);
ALTER TABLE service_order_saga ADD COLUMN recovery_owner uuid;
ALTER TABLE service_order_saga ADD COLUMN recovery_until timestamptz;
ALTER TABLE service_order_saga ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE service_order_saga ADD COLUMN recovery_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE saga_operation ALTER COLUMN item_id DROP NOT NULL;
ALTER TABLE saga_operation ALTER COLUMN composition_version DROP NOT NULL;
ALTER TABLE saga_operation ALTER COLUMN budget_revision DROP NOT NULL;
ALTER TABLE saga_operation ALTER COLUMN attempt DROP NOT NULL;
ALTER TABLE saga_operation DROP CONSTRAINT saga_operation_state_check;
ALTER TABLE saga_operation ADD CHECK (state IN ('START_PENDING','AWAITING_RESULT','CONFIRMED','REJECTED','PENDING','SUCCEEDED','SKIPPED','NEEDS_REVIEW'));
ALTER TABLE saga_operation ADD COLUMN original_operation_id uuid REFERENCES saga_operation(operation_id);
ALTER TABLE saga_operation ADD COLUMN budget_id uuid;
ALTER TABLE saga_operation ADD COLUMN late_result jsonb;
ALTER TABLE saga_operation ADD COLUMN attempts integer NOT NULL DEFAULT 0;
ALTER TABLE saga_operation ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE saga_operation ADD COLUMN lease_owner uuid;
ALTER TABLE saga_operation ADD COLUMN lease_until timestamptz;
CREATE UNIQUE INDEX one_compensation_per_consumption ON saga_operation(original_operation_id) WHERE intent='RESOLVE_STOCK';
CREATE UNIQUE INDEX one_billing_cancellation ON saga_operation(service_order_id) WHERE intent IN ('CANCEL_BUDGET','CLOSE_BUDGET_CONTEXT');
CREATE INDEX saga_recovery_due ON service_order_saga(next_attempt_at) WHERE state='COMPENSATING' OR settlement_pending;
CREATE INDEX operation_recovery_due ON saga_operation(next_attempt_at) WHERE state IN ('START_PENDING','AWAITING_RESULT','PENDING');
ALTER TABLE saga_operation ADD CHECK (intent IN ('START_ITEM','RESOLVE_STOCK','CANCEL_BUDGET','CLOSE_BUDGET_CONTEXT'));
ALTER TABLE saga_operation ADD CHECK (intent <> 'START_ITEM' OR (item_id IS NOT NULL AND composition_version IS NOT NULL AND budget_revision IS NOT NULL AND attempt IS NOT NULL));
ALTER TABLE saga_operation ADD CHECK ((intent='RESOLVE_STOCK') = (original_operation_id IS NOT NULL));
ALTER TABLE saga_operation ADD CHECK ((intent='CANCEL_BUDGET') = (budget_id IS NOT NULL));

-- Keep approved composition immutable. Only C6 may cancel its operational items,
-- after all persisted remote effects have been confirmed in this transaction.
CREATE OR REPLACE FUNCTION protect_approved_item_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM ordem_servico WHERE id=OLD.id_ordem_servico AND approved_budget_id IS NOT NULL)
       AND NOT (NEW.status='CANCELLED' AND OLD.start_date_time IS NULL AND EXISTS (
           SELECT 1 FROM service_order_saga s WHERE s.service_order_id=OLD.id_ordem_servico
             AND s.state='COMPENSATED' AND s.cancellation IS NOT NULL
             AND EXISTS (SELECT 1 FROM saga_operation p WHERE p.service_order_id=s.service_order_id
                 AND p.intent IN ('CANCEL_BUDGET','CLOSE_BUDGET_CONTEXT') AND p.state='SUCCEEDED')
             AND NOT EXISTS (SELECT 1 FROM saga_operation p WHERE p.service_order_id=s.service_order_id
                 AND p.state NOT IN ('SUCCEEDED','SKIPPED','REJECTED')))) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='APPROVED_COMPOSITION_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_saga_operation_request() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.operation_id,OLD.service_order_id,OLD.item_id,OLD.intent,OLD.original_operation_id,OLD.request,
           OLD.composition_version,OLD.budget_revision,OLD.attempt,OLD.budget_id) IS DISTINCT FROM
       ROW(NEW.operation_id,NEW.service_order_id,NEW.item_id,NEW.intent,NEW.original_operation_id,NEW.request,
           NEW.composition_version,NEW.budget_revision,NEW.attempt,NEW.budget_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='SAGA_OPERATION_REQUEST_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER saga_operation_request_immutable BEFORE UPDATE ON saga_operation
    FOR EACH ROW EXECUTE FUNCTION protect_saga_operation_request();
