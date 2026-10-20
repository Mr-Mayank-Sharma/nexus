-- A real history for every order: creation and each status change are written to nx_audit_log by the
-- database itself, so imports, schedulers, manual actions and AI actions are all captured. The acting
-- user comes from the session setting app.current_user (set per request by TenantAwareDataSource).
CREATE OR REPLACE FUNCTION nx_log_order_status() RETURNS trigger AS $$
DECLARE
    actor text := NULLIF(current_setting('app.current_user', true), '');
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO nx_audit_log (tenant_id, entity_type, entity_id, event_type, actor_type, data)
        VALUES (NEW.tenant_id, 'ORDER', NEW.id, 'ORDER_CREATED', COALESCE(actor, 'SYSTEM'),
                jsonb_build_object('to', NEW.status, 'channel', NEW.channel, 'user', actor));
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO nx_audit_log (tenant_id, entity_type, entity_id, event_type, actor_type, data)
        VALUES (NEW.tenant_id, 'ORDER', NEW.id, 'STATUS_CHANGED', COALESCE(actor, 'SYSTEM'),
                jsonb_build_object('from', OLD.status, 'to', NEW.status, 'subStatus', NEW.sub_status, 'user', actor));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_nx_order_status_history ON nx_orders;
CREATE TRIGGER trg_nx_order_status_history
    AFTER INSERT OR UPDATE OF status ON nx_orders
    FOR EACH ROW EXECUTE FUNCTION nx_log_order_status();
