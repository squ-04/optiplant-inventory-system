CREATE INDEX ix_purchase_branch_history ON purchases_order(branch_id,created_at,id);
CREATE INDEX ix_purchase_supplier_history ON purchases_order(supplier_id,created_at,id);
CREATE INDEX ix_purchase_pending ON purchases_order(status,branch_id);
CREATE INDEX ix_purchase_line_product ON purchases_line(product_id,order_id);
CREATE INDEX ix_purchase_line_unit ON purchases_line(product_unit_id,product_id);
CREATE INDEX ix_sale_branch_history ON sales_sale(branch_id,confirmed_at,id);
CREATE INDEX ix_sale_actor_history ON sales_sale(confirmed_by,confirmed_at,id);
CREATE INDEX ix_sale_line_product ON sales_line(product_id,sale_id);
CREATE INDEX ix_sale_line_unit ON sales_line(product_unit_id,product_id);
CREATE INDEX ix_transfer_origin_pending ON transfers_transfer(origin_branch_id,status,requested_at);
CREATE INDEX ix_transfer_destination_pending ON transfers_transfer(destination_branch_id,status,requested_at);
CREATE INDEX ix_transfer_requested ON transfers_transfer(requested_at,id);
CREATE INDEX ix_transfer_line_product ON transfers_line(product_id,transfer_id);
CREATE INDEX ix_transfer_line_unit ON transfers_line(product_unit_id,product_id);
CREATE INDEX ix_logistics_route_priority ON logistics_route(priority);
CREATE INDEX ix_logistics_dispatch_eta ON logistics_dispatch(estimated_arrival_at,id);
CREATE INDEX ix_logistics_arrival_time ON logistics_arrival(arrived_at,id);
CREATE INDEX ix_logistics_discrepancy_status ON logistics_discrepancy(status,opened_at,id);
CREATE INDEX ix_inventory_balance_product ON inventory_balance(product_id,branch_id);
CREATE INDEX ix_inventory_operation_time ON inventory_operation(occurred_at,id);
CREATE INDEX ix_inventory_operation_actor ON inventory_operation(actor_id,occurred_at,id);
CREATE INDEX ix_inventory_movement_branch ON inventory_movement(branch_id,product_id,operation_id);
CREATE INDEX ix_inventory_movement_product ON inventory_movement(product_id,operation_id);
CREATE INDEX ix_inventory_movement_unit ON inventory_movement(product_unit_id,product_id);
CREATE INDEX ix_inventory_movement_purchase ON inventory_movement(purchase_line_id) WHERE purchase_line_id IS NOT NULL;
CREATE INDEX ix_inventory_movement_sale ON inventory_movement(sale_line_id) WHERE sale_line_id IS NOT NULL;
CREATE INDEX ix_inventory_movement_dispatch ON inventory_movement(dispatch_line_id) WHERE dispatch_line_id IS NOT NULL;
CREATE INDEX ix_inventory_movement_arrival ON inventory_movement(arrival_line_id) WHERE arrival_line_id IS NOT NULL;
CREATE INDEX ix_inventory_reorder_time ON inventory_reorder_alert(opened_at,id);
CREATE INDEX ix_inventory_reorder_history ON inventory_reorder_alert(branch_product_id,resolved_at);

-- Completa la regla exhaustiva §8.3: indexar FK aún no cubiertas por prefijo.
-- Los índices existentes (incluidos UNIQUE) son la autoridad para evitar duplicados.
DO $$
DECLARE
    fk RECORD;
    columns_sql TEXT;
BEGIN
    FOR fk IN
        SELECT c.conrelid, c.conkey, c.conname, t.relname
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE c.contype='f' AND n.nspname='public'
          AND t.relname NOT IN ('spring_session_attributes')
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_index i WHERE i.indrelid=fk.conrelid AND i.indisvalid
            AND i.indpred IS NULL
            AND i.indkey[0]=fk.conkey[1]
            AND (cardinality(fk.conkey)=1 OR i.indkey[1]=fk.conkey[2])
        ) THEN
            SELECT string_agg(quote_ident(a.attname),',' ORDER BY k.ordinality) INTO columns_sql
            FROM unnest(fk.conkey) WITH ORDINALITY k(attnum,ordinality)
            JOIN pg_attribute a ON a.attrelid=fk.conrelid AND a.attnum=k.attnum;
            EXECUTE format('CREATE INDEX %I ON %I (%s)',
                'ix_fk_' || substr(fk.relname,1,35) || '_' || substr(md5(fk.conname),1,12), fk.relname, columns_sql);
        END IF;
    END LOOP;
END $$;
