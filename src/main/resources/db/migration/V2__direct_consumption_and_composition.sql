ALTER TABLE material_previsto DROP CONSTRAINT ck_material_quantities;
ALTER TABLE material_previsto ADD CONSTRAINT ck_material_quantities CHECK (
    reserved_quantity BETWEEN 0 AND planned_quantity AND consumed_quantity BETWEEN 0 AND planned_quantity
);

COMMENT ON COLUMN material_previsto.reserved_quantity IS 'Legacy V1 reservation audit; not proof of direct consumption';
COMMENT ON COLUMN material_previsto.reservation_id IS 'Legacy V1 reservation reference';

-- V1 permitted incomplete evidence. Preserve it for reconciliation and enforce the
-- new rule on inserts and changes to consumption evidence, including reference deletion.
CREATE FUNCTION check_material_consumption_references(material_allocation_id uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    allocation material_previsto%ROWTYPE;
    has_movement boolean;
    nil_uuid constant uuid := '00000000-0000-0000-0000-000000000000';
BEGIN
    SELECT * INTO allocation FROM material_previsto WHERE id = material_allocation_id FOR NO KEY UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    SELECT EXISTS (SELECT 1 FROM material_movement_reference WHERE allocation_id = allocation.id) INTO has_movement;
    IF (allocation.consumed_quantity = 0 AND (allocation.consumption_operation_id IS NOT NULL OR has_movement))
       OR (allocation.consumed_quantity > 0 AND (
           allocation.consumption_operation_id IS NULL OR allocation.consumption_operation_id = nil_uuid
           OR NOT has_movement OR EXISTS (SELECT 1 FROM material_movement_reference
                                          WHERE allocation_id = allocation.id AND movement_id = nil_uuid)
       )) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'MATERIAL_CONSUMPTION_REFERENCES_INVALID';
    END IF;
END;
$$;

CREATE FUNCTION validate_material_consumption_references() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'material_previsto' THEN
        PERFORM check_material_consumption_references(NEW.id);
    ELSE
        IF TG_OP <> 'INSERT' THEN
            PERFORM check_material_consumption_references(OLD.allocation_id);
        END IF;
        IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.allocation_id <> OLD.allocation_id) THEN
            PERFORM check_material_consumption_references(NEW.allocation_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER material_consumption_insert
    AFTER INSERT ON material_previsto DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_material_consumption_references();
CREATE CONSTRAINT TRIGGER material_consumption_update
    AFTER UPDATE ON material_previsto DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    WHEN (ROW(OLD.consumed_quantity, OLD.consumption_operation_id) IS DISTINCT FROM
          ROW(NEW.consumed_quantity, NEW.consumption_operation_id))
    EXECUTE FUNCTION validate_material_consumption_references();
CREATE CONSTRAINT TRIGGER material_consumption_movements
    AFTER INSERT OR UPDATE OR DELETE ON material_movement_reference DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_material_consumption_references();

ALTER TABLE ordem_servico DROP CONSTRAINT ordem_servico_status_check;
UPDATE ordem_servico SET status = 'APPROVED' WHERE status = 'READY_FOR_EXECUTION';
ALTER TABLE ordem_servico ADD CONSTRAINT ordem_servico_status_check CHECK (status IN (
    'RECEIVED', 'IN_DIAGNOSIS', 'WAITING_APPROVAL', 'APPROVED', 'REJECTED',
    'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DELIVERED'
));

ALTER TABLE item_ordem_servico ADD COLUMN start_pending boolean NOT NULL DEFAULT false;
ALTER TABLE item_ordem_servico DROP CONSTRAINT item_ordem_servico_status_check;
UPDATE item_ordem_servico SET status = 'PENDING', start_pending = true WHERE status = 'START_REQUESTED';
ALTER TABLE item_ordem_servico ADD CONSTRAINT item_ordem_servico_status_check CHECK (status IN (
    'PENDING', 'WAITING_FOR_PARTS_AND_SUPPLIES', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
));
ALTER TABLE item_ordem_servico ADD CONSTRAINT ck_item_start_pending CHECK (
    NOT start_pending OR status IN ('PENDING', 'WAITING_FOR_PARTS_AND_SUPPLIES')
);

CREATE TABLE service_order_composition (
    service_order_id uuid NOT NULL REFERENCES ordem_servico(id),
    composition_version integer NOT NULL CHECK (composition_version > 0),
    items jsonb NOT NULL CHECK (jsonb_typeof(items) = 'array'),
    PRIMARY KEY (service_order_id, composition_version)
);

-- This is the first local technical snapshot. V1 commercial approvals remain
-- readable, with their original JSON, and are never retroactively linked to it.
INSERT INTO service_order_composition (service_order_id, composition_version, items)
SELECT o.id, 1, COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
        'serviceOrderItemId', i.id, 'automotiveServiceId', i.id_servico, 'quantity', i.quantity,
        'optional', i.optional, 'serviceAmount', (i.labor_unit_price * i.quantity)::text, 'supplies', COALESCE((
            SELECT jsonb_agg(jsonb_build_object('inventoryItemId', m.material_id, 'quantity', m.planned_quantity)
                             ORDER BY m.material_id)
            FROM material_previsto m WHERE m.id_item_ordem_servico = i.id
        ), '[]'::jsonb)
    ) ORDER BY i.id)
    FROM item_ordem_servico i WHERE i.id_ordem_servico = o.id AND i.status <> 'CANCELLED'
), '[]'::jsonb)
FROM ordem_servico o;

ALTER TABLE ordem_servico
    ADD COLUMN composition_version integer NOT NULL DEFAULT 1,
    ADD COLUMN current_budget_id uuid,
    ADD COLUMN budget_composition_version integer,
    ADD COLUMN approved_composition_version integer,
    ADD CONSTRAINT fk_current_composition FOREIGN KEY (id, composition_version)
        REFERENCES service_order_composition (service_order_id, composition_version) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT ck_budget_composition CHECK (
        (current_budget_id IS NULL AND budget_composition_version IS NULL)
        OR (current_budget_id IS NOT NULL AND budget_composition_version IS NOT NULL
            AND budget_composition_version = composition_version)
    );

ALTER TABLE ordem_servico DROP CONSTRAINT ck_approved_snapshot;
ALTER TABLE ordem_servico ADD CONSTRAINT ck_approved_snapshot CHECK (
    (approved_budget_id IS NULL AND approved_revision IS NULL AND approved_currency IS NULL
        AND approved_amount IS NULL AND approved_items_json IS NULL AND approved_composition_version IS NULL)
    OR (approved_budget_id IS NOT NULL AND approved_revision IS NOT NULL
        AND approved_revision = budget_revision AND approved_currency IS NOT NULL
        AND approved_currency ~ '^[A-Z]{3}$' AND approved_amount IS NOT NULL AND approved_amount >= 0
        AND ((approved_composition_version IS NULL AND current_budget_id IS NULL
                  AND approved_items_json IS NOT NULL AND jsonb_typeof(approved_items_json) = 'array')
             OR (approved_composition_version IS NOT NULL AND current_budget_id IS NOT NULL
                  AND approved_budget_id = current_budget_id AND approved_composition_version = composition_version
                  AND approved_items_json IS NULL)))
);

CREATE FUNCTION protect_composition_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'COMPOSITION_SNAPSHOT_IMMUTABLE';
END;
$$;
CREATE TRIGGER composition_snapshot_immutable BEFORE UPDATE OR DELETE ON service_order_composition
    FOR EACH ROW EXECUTE FUNCTION protect_composition_snapshot();
CREATE TRIGGER approved_composition_link_immutable BEFORE UPDATE ON ordem_servico
    FOR EACH ROW WHEN (OLD.approved_budget_id IS NOT NULL AND
        ROW(OLD.composition_version, OLD.current_budget_id, OLD.budget_composition_version, OLD.approved_composition_version)
        IS DISTINCT FROM
        ROW(NEW.composition_version, NEW.current_budget_id, NEW.budget_composition_version, NEW.approved_composition_version))
    EXECUTE FUNCTION protect_composition_snapshot();

CREATE FUNCTION protect_approved_item_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM ordem_servico WHERE id = OLD.id_ordem_servico AND approved_budget_id IS NOT NULL) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_COMPOSITION_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER approved_item_membership_immutable BEFORE UPDATE ON item_ordem_servico FOR EACH ROW
    WHEN ((OLD.status = 'CANCELLED') <> (NEW.status = 'CANCELLED'))
    EXECUTE FUNCTION protect_approved_item_membership();
