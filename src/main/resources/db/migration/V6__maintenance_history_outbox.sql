CREATE TABLE maintenance_history_outbox (
    service_order_id uuid NOT NULL REFERENCES ordem_servico(id),
    history_version integer NOT NULL CHECK (history_version > 0),
    vehicle_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    completed_at timestamptz NOT NULL,
    history_key text NOT NULL,
    snapshot text NOT NULL,
    state varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','PROJECTED','COLLISION')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner uuid,
    lease_until timestamptz,
    last_error varchar(40),
    projected_at timestamptz,
    PRIMARY KEY (service_order_id, history_version),
    UNIQUE (vehicle_id, history_key),
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CHECK (state = 'PENDING' OR lease_owner IS NULL),
    CHECK ((state = 'PROJECTED') = (projected_at IS NOT NULL))
);

CREATE INDEX ix_maintenance_history_due ON maintenance_history_outbox(next_attempt_at, completed_at)
    WHERE state = 'PENDING';
CREATE INDEX ix_order_vehicle_branch ON ordem_servico(id_veiculo, branch_id);

CREATE FUNCTION protect_maintenance_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'MAINTENANCE_SNAPSHOT_IMMUTABLE';
    END IF;
    IF ROW(NEW.service_order_id, NEW.history_version, NEW.vehicle_id, NEW.branch_id,
           NEW.completed_at, NEW.history_key, NEW.snapshot) IS DISTINCT FROM
       ROW(OLD.service_order_id, OLD.history_version, OLD.vehicle_id, OLD.branch_id,
           OLD.completed_at, OLD.history_key, OLD.snapshot) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'MAINTENANCE_SNAPSHOT_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER maintenance_snapshot_immutable BEFORE UPDATE OR DELETE ON maintenance_history_outbox
    FOR EACH ROW EXECUTE FUNCTION protect_maintenance_snapshot();
