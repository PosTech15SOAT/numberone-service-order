\set ON_ERROR_STOP on

-- Usage: psql ... -v order_id=<uuid> -v history_version=1 -f scripts/reprocess-maintenance-history.sql
WITH target AS MATERIALIZED (
    SELECT service_order_id, history_version, state,
           COALESCE(lease_until > CURRENT_TIMESTAMP, false) AS in_flight,
           state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
               AND lease_owner IS NULL AS already_due
    FROM maintenance_history_outbox
    WHERE service_order_id = :'order_id'::uuid AND history_version = :'history_version'::integer
    FOR UPDATE
), requeued AS (
    UPDATE maintenance_history_outbox AS outbox
    SET state='PENDING', next_attempt_at=CURRENT_TIMESTAMP, projected_at=NULL,
        lease_owner=NULL, lease_until=NULL, last_error=NULL
    FROM target
    WHERE (outbox.service_order_id, outbox.history_version) = (target.service_order_id, target.history_version)
      AND NOT target.in_flight AND NOT target.already_due
    RETURNING 1
)
SELECT (SELECT count(*) FROM target) AS matched,
       (SELECT count(*) FROM requeued) AS requeued,
       (SELECT count(*) FROM target WHERE in_flight) AS in_flight,
       (SELECT count(*) FROM target WHERE already_due) AS already_due,
       (SELECT count(*) FROM target WHERE state='COLLISION') AS previous_collisions;
