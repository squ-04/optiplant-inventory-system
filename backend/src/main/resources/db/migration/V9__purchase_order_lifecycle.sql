-- RF-0022 / INC-011/D03: only draft orders are rewritten. Editing a draft replaces its lines, so the runtime role may
-- delete purchase lines; the trigger rejects it for any order that is no longer a draft. Orders themselves are never
-- deleted (cancellation keeps them), so DELETE on purchases_order stays ungranted.
GRANT DELETE ON purchases_line TO optiplant_runtime;

CREATE FUNCTION purchases_line_delete_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT status FROM purchases_order WHERE id=OLD.order_id) <> 'DRAFT' THEN
        RAISE EXCEPTION 'PURCHASE_LINE_IMMUTABLE: only the lines of a draft order can be deleted';
    END IF;
    RETURN OLD;
END $$;

CREATE TRIGGER purchases_line_delete_guard BEFORE DELETE ON purchases_line
    FOR EACH ROW EXECUTE FUNCTION purchases_line_delete_guard();

-- RF-0022-CA-02: a cancelled order is replaced by at most one new order.
CREATE UNIQUE INDEX ux_purchases_order_replaces ON purchases_order(replaces_order_id) WHERE replaces_order_id IS NOT NULL;
