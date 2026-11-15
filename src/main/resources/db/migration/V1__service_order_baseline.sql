-- New OS database only. No legacy migrations, inventory, billing, RBAC or production data.
CREATE TABLE cliente (
    id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
    nome varchar(90) NOT NULL, tipo_documento varchar(30) NOT NULL,
    documento varchar(50) NOT NULL, telefone varchar(15) NOT NULL,
    endereco varchar(90) NOT NULL, ativo boolean NOT NULL DEFAULT true,
    email varchar(120) NOT NULL, created_at timestamp NOT NULL, updated_at timestamp,
    CONSTRAINT customer_document_type CHECK (tipo_documento IN ('PESSOA_FISICA','PESSOA_JURIDICA'))
);
CREATE UNIQUE INDEX uq_customer_document ON cliente (tipo_documento, regexp_replace(documento, '\D', '', 'g'));

CREATE TABLE veiculo (
    id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
    placa varchar(10) NOT NULL, marca varchar(100) NOT NULL, modelo varchar(100) NOT NULL,
    ano integer NOT NULL CHECK (ano BETWEEN 1886 AND 2100),
    id_cliente uuid NOT NULL REFERENCES cliente(id),
    created_at timestamp NOT NULL, updated_at timestamp,
    CONSTRAINT uq_vehicle_customer UNIQUE(id, id_cliente)
);
CREATE UNIQUE INDEX uq_vehicle_plate ON veiculo (upper(placa));

CREATE TABLE servico_automotivo (
    id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
    codigo varchar(100) NOT NULL UNIQUE, nome varchar(150) NOT NULL,
    descricao varchar(255), tipo_servico varchar(255),
    valor_base numeric(10,2) NOT NULL CHECK (valor_base >= 0),
    tempo_estimado_minutos integer NOT NULL CHECK (tempo_estimado_minutos > 0),
    ativo boolean NOT NULL, created_at timestamp NOT NULL, updated_at timestamp
);

CREATE TABLE ordem_servico (
    id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
    branch_id uuid NOT NULL, id_cliente uuid NOT NULL REFERENCES cliente(id),
    id_veiculo uuid NOT NULL, attempt_id uuid NOT NULL UNIQUE,
    budget_revision integer NOT NULL CHECK (budget_revision > 0),
    initial_description varchar(2000) NOT NULL, diagnosis_description varchar(2000) NOT NULL,
    final_diagnosis_description varchar(2000), notes varchar(2000),
    status varchar(40) NOT NULL CHECK (status IN ('RECEIVED','IN_DIAGNOSIS','WAITING_APPROVAL',
        'APPROVED','REJECTED','READY_FOR_EXECUTION','IN_PROGRESS','COMPLETED','CANCELLED','DELIVERED')),
    entry_date_time timestamp NOT NULL, expected_date_time timestamp, delivery_date_time timestamp,
    created_at timestamp NOT NULL, updated_at timestamp NOT NULL,
    approved_budget_id uuid, approved_revision integer, approved_currency varchar(3),
    approved_amount numeric(19,2), approved_items_json jsonb, payment_confirmed_at timestamp,
    CONSTRAINT fk_order_vehicle_customer FOREIGN KEY(id_veiculo, id_cliente) REFERENCES veiculo(id, id_cliente),
    CONSTRAINT ck_order_dates CHECK (expected_date_time IS NULL OR expected_date_time >= entry_date_time),
    CONSTRAINT ck_delivery CHECK ((status = 'DELIVERED') = (delivery_date_time IS NOT NULL)),
    CONSTRAINT ck_approved_snapshot CHECK (
        (approved_budget_id IS NULL AND approved_revision IS NULL AND approved_currency IS NULL
         AND approved_amount IS NULL AND approved_items_json IS NULL)
        OR (approved_budget_id IS NOT NULL AND approved_revision IS NOT NULL
         AND approved_revision = budget_revision AND approved_currency IS NOT NULL
         AND approved_currency ~ '^[A-Z]{3}$' AND approved_amount IS NOT NULL AND approved_amount >= 0
         AND approved_items_json IS NOT NULL AND jsonb_typeof(approved_items_json) = 'array')),
    CONSTRAINT ck_payment_requires_snapshot CHECK (payment_confirmed_at IS NULL OR approved_budget_id IS NOT NULL)
);
CREATE INDEX ix_order_branch_created ON ordem_servico(branch_id, created_at DESC);
CREATE INDEX ix_order_customer ON ordem_servico(id_cliente);

CREATE TABLE item_ordem_servico (
    id uuid PRIMARY KEY, id_ordem_servico uuid NOT NULL REFERENCES ordem_servico(id),
    id_servico uuid NOT NULL REFERENCES servico_automotivo(id),
    service_description varchar(256) NOT NULL, labor_unit_price numeric(19,2) NOT NULL CHECK (labor_unit_price >= 0),
    quantity integer NOT NULL CHECK (quantity > 0), estimated_time_minutes integer NOT NULL CHECK (estimated_time_minutes > 0),
    optional boolean NOT NULL,
    status varchar(40) NOT NULL CHECK (status IN ('PENDING','WAITING_FOR_PARTS_AND_SUPPLIES',
        'START_REQUESTED','IN_PROGRESS','CANCELLED','COMPLETED')),
    start_date_time timestamp, end_date_time timestamp,
    CONSTRAINT ck_confirmed_item_start CHECK (status NOT IN ('IN_PROGRESS','COMPLETED') OR start_date_time IS NOT NULL),
    CONSTRAINT ck_item_completion CHECK (status <> 'COMPLETED' OR (end_date_time IS NOT NULL AND end_date_time >= start_date_time))
);
CREATE INDEX ix_item_order ON item_ordem_servico(id_ordem_servico);

CREATE TABLE material_previsto (
    id uuid PRIMARY KEY, id_item_ordem_servico uuid NOT NULL REFERENCES item_ordem_servico(id),
    material_id uuid NOT NULL, description varchar(256) NOT NULL,
    reference_unit_price numeric(19,2) NOT NULL CHECK (reference_unit_price >= 0),
    planned_quantity integer NOT NULL CHECK (planned_quantity > 0),
    reserved_quantity integer NOT NULL DEFAULT 0,
    consumed_quantity integer NOT NULL DEFAULT 0,
    reservation_id uuid, consumption_operation_id uuid,
    CONSTRAINT uq_material_per_item UNIQUE(id_item_ordem_servico, material_id),
    CONSTRAINT ck_material_quantities CHECK (reserved_quantity BETWEEN 0 AND planned_quantity
        AND consumed_quantity BETWEEN 0 AND reserved_quantity),
    CONSTRAINT ck_material_reservation CHECK (reserved_quantity = 0 OR reservation_id IS NOT NULL),
    CONSTRAINT ck_material_consumption CHECK (consumed_quantity = 0 OR consumption_operation_id IS NOT NULL)
);
-- IDs returned by Inventory, not an inventory movement ledger or an external FK.
CREATE TABLE material_movement_reference (
    allocation_id uuid NOT NULL REFERENCES material_previsto(id),
    movement_id uuid NOT NULL,
    PRIMARY KEY(allocation_id, movement_id)
);

CREATE FUNCTION protect_approved_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.approved_budget_id IS NOT NULL AND (
        ROW(NEW.approved_budget_id, NEW.approved_revision, NEW.approved_currency, NEW.approved_amount,
            NEW.approved_items_json, NEW.budget_revision, NEW.initial_description, NEW.diagnosis_description,
            NEW.final_diagnosis_description, NEW.notes, NEW.id_cliente, NEW.id_veiculo, NEW.branch_id, NEW.attempt_id, NEW.entry_date_time, NEW.expected_date_time)
        IS DISTINCT FROM
        ROW(OLD.approved_budget_id, OLD.approved_revision, OLD.approved_currency, OLD.approved_amount,
            OLD.approved_items_json, OLD.budget_revision, OLD.initial_description, OLD.diagnosis_description,
            OLD.final_diagnosis_description, OLD.notes, OLD.id_cliente, OLD.id_veiculo, OLD.branch_id, OLD.attempt_id, OLD.entry_date_time, OLD.expected_date_time)) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_SNAPSHOT_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER approved_order_immutable BEFORE UPDATE ON ordem_servico
    FOR EACH ROW EXECUTE FUNCTION protect_approved_order();

CREATE FUNCTION protect_approved_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE order_id uuid;
BEGIN
    order_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.id_ordem_servico ELSE NEW.id_ordem_servico END;
    IF EXISTS (SELECT 1 FROM ordem_servico WHERE approved_budget_id IS NOT NULL
        AND (id = order_id OR (TG_OP = 'UPDATE' AND id = OLD.id_ordem_servico))) THEN
        IF TG_OP <> 'UPDATE' THEN
            RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_COMPOSITION_IMMUTABLE';
        END IF;
        IF ROW(NEW.id_ordem_servico, NEW.id_servico, NEW.service_description, NEW.labor_unit_price, NEW.quantity, NEW.optional, NEW.estimated_time_minutes)
            IS DISTINCT FROM ROW(OLD.id_ordem_servico, OLD.id_servico, OLD.service_description, OLD.labor_unit_price, OLD.quantity, OLD.optional, OLD.estimated_time_minutes) THEN
            RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_COMPOSITION_IMMUTABLE';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER approved_item_immutable BEFORE INSERT OR UPDATE OR DELETE ON item_ordem_servico
    FOR EACH ROW EXECUTE FUNCTION protect_approved_item();

CREATE FUNCTION protect_approved_material() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item_id uuid;
BEGIN
    item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.id_item_ordem_servico ELSE NEW.id_item_ordem_servico END;
    IF EXISTS (SELECT 1 FROM item_ordem_servico i JOIN ordem_servico o ON o.id = i.id_ordem_servico
               WHERE o.approved_budget_id IS NOT NULL AND (i.id = item_id
                   OR (TG_OP = 'UPDATE' AND i.id = OLD.id_item_ordem_servico))) THEN
        IF TG_OP <> 'UPDATE' THEN
            RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_COMPOSITION_IMMUTABLE';
        END IF;
        IF ROW(NEW.id_item_ordem_servico, NEW.material_id, NEW.description, NEW.reference_unit_price, NEW.planned_quantity)
            IS DISTINCT FROM ROW(OLD.id_item_ordem_servico, OLD.material_id, OLD.description, OLD.reference_unit_price, OLD.planned_quantity) THEN
            RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'APPROVED_COMPOSITION_IMMUTABLE';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER approved_material_immutable BEFORE INSERT OR UPDATE OR DELETE ON material_previsto
    FOR EACH ROW EXECUTE FUNCTION protect_approved_material();
