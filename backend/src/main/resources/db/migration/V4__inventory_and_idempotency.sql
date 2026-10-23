CREATE TABLE inventory_balance (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_balance PRIMARY KEY CHECK(id>0),
    branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    physical_qty NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK(physical_qty>=0 AND physical_qty<>'NaN'::numeric),
    inventory_value NUMERIC(60,18) NOT NULL DEFAULT 0 CHECK(inventory_value<>'NaN'::numeric),
    average_base_cost NUMERIC(33,6) NOT NULL DEFAULT 0 CHECK(average_base_cost<>'NaN'::numeric),
    history_started_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(branch_id,product_id), CHECK(physical_qty<>0 OR inventory_value=0)
);
CREATE TABLE inventory_operation (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_operation PRIMARY KEY CHECK(id>0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    kind VARCHAR(24) NOT NULL CHECK(kind IN ('PURCHASE_RECEIPT','SALE','DISPATCH','ARRIVAL','MANUAL_ENTRY','MANUAL_EXIT','SALE_RETURN','COUNT_ADJUSTMENT','ENTRY_CORRECTION','TRANSIT_LOSS')),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    authorized_by BIGINT REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    reason TEXT NOT NULL CHECK(btrim(reason)<>''),
    evidence TEXT,
    purchase_order_id BIGINT REFERENCES purchases_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    sale_id BIGINT REFERENCES sales_sale(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    dispatch_id BIGINT REFERENCES logistics_dispatch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    arrival_id BIGINT REFERENCES logistics_arrival(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    discrepancy_id BIGINT REFERENCES logistics_discrepancy(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    original_operation_id BIGINT REFERENCES inventory_operation(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(original_operation_id<>id),
    CHECK((kind='PURCHASE_RECEIPT' AND purchase_order_id IS NOT NULL AND num_nonnulls(purchase_order_id,sale_id,dispatch_id,arrival_id)=1)
        OR (kind='SALE' AND sale_id IS NOT NULL AND num_nonnulls(purchase_order_id,sale_id,dispatch_id,arrival_id)=1)
        OR (kind='DISPATCH' AND dispatch_id IS NOT NULL AND num_nonnulls(purchase_order_id,sale_id,dispatch_id,arrival_id)=1)
        OR (kind='ARRIVAL' AND arrival_id IS NOT NULL AND num_nonnulls(purchase_order_id,sale_id,dispatch_id,arrival_id)=1)
        OR (kind NOT IN ('PURCHASE_RECEIPT','SALE','DISPATCH','ARRIVAL') AND num_nonnulls(purchase_order_id,sale_id,dispatch_id,arrival_id)=0)),
    CHECK(kind<>'ENTRY_CORRECTION' OR original_operation_id IS NOT NULL),
    CHECK(kind<>'TRANSIT_LOSS' OR (discrepancy_id IS NOT NULL AND authorized_by IS NOT NULL AND evidence IS NOT NULL AND btrim(evidence)<>''))
);
CREATE UNIQUE INDEX uq_inventory_operation_purchase ON inventory_operation(purchase_order_id) WHERE purchase_order_id IS NOT NULL;
CREATE UNIQUE INDEX uq_inventory_operation_sale ON inventory_operation(sale_id) WHERE sale_id IS NOT NULL;
CREATE UNIQUE INDEX uq_inventory_operation_dispatch ON inventory_operation(dispatch_id) WHERE dispatch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_inventory_operation_arrival ON inventory_operation(arrival_id) WHERE arrival_id IS NOT NULL;
CREATE TABLE inventory_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_movement PRIMARY KEY CHECK(id>0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    operation_id BIGINT NOT NULL REFERENCES inventory_operation(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_unit_id BIGINT NOT NULL,
    direction VARCHAR(3) NOT NULL CHECK(direction IN ('IN','OUT')),
    commercial_qty NUMERIC(18,3) NOT NULL CHECK(commercial_qty>0 AND commercial_qty<>'NaN'::numeric),
    base_qty NUMERIC(18,3) NOT NULL CHECK(base_qty>0 AND base_qty<>'NaN'::numeric),
    factor_snapshot NUMERIC(24,9) NOT NULL CHECK(factor_snapshot>0 AND factor_snapshot<>'NaN'::numeric),
    sku_snapshot VARCHAR(64) NOT NULL CHECK(btrim(sku_snapshot)<>''),
    product_name_snapshot VARCHAR(160) NOT NULL CHECK(btrim(product_name_snapshot)<>''),
    unit_name_snapshot VARCHAR(64) NOT NULL CHECK(btrim(unit_name_snapshot)<>''),
    historical_base_cost NUMERIC(33,6) NOT NULL CHECK(historical_base_cost<>'NaN'::numeric),
    purchase_line_id BIGINT REFERENCES purchases_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    sale_line_id BIGINT REFERENCES sales_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    dispatch_line_id BIGINT REFERENCES logistics_dispatch_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    arrival_line_id BIGINT REFERENCES logistics_arrival_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    original_movement_id BIGINT REFERENCES inventory_movement(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(product_unit_id,product_id) REFERENCES catalog_product_unit(id,product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    UNIQUE(operation_id,purchase_line_id), UNIQUE(operation_id,sale_line_id),
    UNIQUE(operation_id,dispatch_line_id), UNIQUE(operation_id,arrival_line_id),
    CHECK(num_nonnulls(purchase_line_id,sale_line_id,dispatch_line_id,arrival_line_id)<=1),
    CHECK(original_movement_id<>id), CHECK(base_qty=commercial_qty*factor_snapshot)
);
CREATE TABLE inventory_valuation_component (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_valuation_component PRIMARY KEY CHECK(id>0),
    movement_id BIGINT NOT NULL REFERENCES inventory_movement(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    component_kind VARCHAR(24) NOT NULL CHECK(component_kind IN ('BASE','FINAL_ROUNDING','HISTORICAL_CORRECTION')),
    amount NUMERIC(60,18) NOT NULL CHECK(amount<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(movement_id,component_kind), CHECK(component_kind='BASE' OR amount<>0)
);
CREATE TABLE inventory_transit (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_transit PRIMARY KEY CHECK(id>0),
    dispatch_line_id BIGINT NOT NULL UNIQUE REFERENCES logistics_dispatch_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    pending_base_qty NUMERIC(18,3) NOT NULL CHECK(pending_base_qty>=0 AND pending_base_qty<>'NaN'::numeric),
    pending_base_value NUMERIC(60,18) NOT NULL CHECK(pending_base_value<>'NaN'::numeric),
    pending_rounding_value NUMERIC(60,18) NOT NULL DEFAULT 0 CHECK(pending_rounding_value<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(pending_base_qty<>0 OR (pending_base_value=0 AND pending_rounding_value=0))
);
CREATE TABLE inventory_transit_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_transit_entry PRIMARY KEY CHECK(id>0),
    transit_id BIGINT NOT NULL REFERENCES inventory_transit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    operation_id BIGINT NOT NULL REFERENCES inventory_operation(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    entry_kind VARCHAR(16) NOT NULL CHECK(entry_kind IN ('DISPATCH','ARRIVAL','LOSS','CORRECTION')),
    qty_delta NUMERIC(18,3) NOT NULL CHECK(qty_delta<>0 AND qty_delta<>'NaN'::numeric),
    base_value_delta NUMERIC(60,18) NOT NULL CHECK(base_value_delta<>'NaN'::numeric),
    rounding_value_delta NUMERIC(60,18) NOT NULL DEFAULT 0 CHECK(rounding_value_delta<>'NaN'::numeric),
    original_entry_id BIGINT REFERENCES inventory_transit_entry(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(transit_id,operation_id), CHECK(original_entry_id<>id),
    CHECK((entry_kind='DISPATCH' AND qty_delta>0) OR (entry_kind IN ('ARRIVAL','LOSS') AND qty_delta<0)
        OR (entry_kind='CORRECTION' AND original_entry_id IS NOT NULL))
);
CREATE TABLE inventory_reorder_alert (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_inventory_reorder_alert PRIMARY KEY CHECK(id>0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    branch_product_id BIGINT NOT NULL REFERENCES catalog_branch_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    opened_at TIMESTAMPTZ NOT NULL,
    minimum_snapshot NUMERIC(18,3) NOT NULL CHECK(minimum_snapshot>=0 AND minimum_snapshot<>'NaN'::numeric),
    quantity_snapshot NUMERIC(18,3) NOT NULL CHECK(quantity_snapshot>=0 AND quantity_snapshot<>'NaN'::numeric),
    resolved_at TIMESTAMPTZ CHECK(resolved_at>=opened_at),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_inventory_reorder_active ON inventory_reorder_alert(branch_product_id) WHERE resolved_at IS NULL;
CREATE TABLE app_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_app_idempotency PRIMARY KEY CHECK(id>0),
    actor_id BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    operation_code VARCHAR(64) NOT NULL CHECK(btrim(operation_code)<>''),
    scope_key VARCHAR(128) NOT NULL CHECK(btrim(scope_key)<>''),
    idempotency_key VARCHAR(128) NOT NULL CHECK(btrim(idempotency_key)<>''),
    request_hash BYTEA NOT NULL CHECK(octet_length(request_hash)=32),
    result_status SMALLINT NOT NULL CHECK(result_status BETWEEN 200 AND 299),
    result_body JSONB NOT NULL,
    result_location VARCHAR(512),
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(actor_id,operation_code,scope_key,idempotency_key)
);
