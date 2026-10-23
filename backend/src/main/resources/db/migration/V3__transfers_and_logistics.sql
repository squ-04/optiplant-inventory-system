CREATE TABLE transfers_transfer (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_transfers_transfer PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code=btrim(code) AND code<>''),
    origin_branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    destination_branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    requested_by BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    requested_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT','RECEIVED_COMPLETE','RECEIVED_DISCREPANCY','CANCELLED','REJECTED')),
    source_discrepancy_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(origin_branch_id<>destination_branch_id)
);
CREATE TABLE transfers_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_transfers_line PRIMARY KEY CHECK (id > 0),
    transfer_id BIGINT NOT NULL REFERENCES transfers_transfer(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    line_no INTEGER NOT NULL CHECK(line_no>0),
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_unit_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL CHECK (btrim(sku_snapshot)<>''),
    product_name_snapshot VARCHAR(160) NOT NULL CHECK (btrim(product_name_snapshot)<>''),
    unit_name_snapshot VARCHAR(64) NOT NULL CHECK (btrim(unit_name_snapshot)<>''),
    factor_snapshot NUMERIC(24,9) NOT NULL CHECK(factor_snapshot>0 AND factor_snapshot<>'NaN'::numeric),
    requested_commercial_qty NUMERIC(18,3) NOT NULL CHECK(requested_commercial_qty>0 AND requested_commercial_qty<>'NaN'::numeric),
    requested_base_qty NUMERIC(18,3) NOT NULL CHECK(requested_base_qty>0 AND requested_base_qty<>'NaN'::numeric),
    prepared_commercial_qty NUMERIC(18,3) CHECK(prepared_commercial_qty>=0 AND prepared_commercial_qty<>'NaN'::numeric),
    prepared_base_qty NUMERIC(18,3) CHECK(prepared_base_qty>=0 AND prepared_base_qty<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(transfer_id,line_no), UNIQUE(transfer_id,product_unit_id),
    FOREIGN KEY(product_unit_id,product_id) REFERENCES catalog_product_unit(id,product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CHECK(requested_base_qty=requested_commercial_qty*factor_snapshot),
    CHECK((prepared_base_qty IS NULL)=(prepared_commercial_qty IS NULL)),
    CHECK(prepared_base_qty<=requested_base_qty),
    CHECK(prepared_base_qty=prepared_commercial_qty*factor_snapshot)
);
CREATE TABLE transfers_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_transfers_event PRIMARY KEY CHECK (id > 0),
    transfer_id BIGINT NOT NULL REFERENCES transfers_transfer(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    sequence_no INTEGER NOT NULL CHECK(sequence_no>0),
    from_status VARCHAR(24) CHECK(from_status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT','RECEIVED_COMPLETE','RECEIVED_DISCREPANCY','CANCELLED','REJECTED')),
    to_status VARCHAR(24) NOT NULL CHECK(to_status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT','RECEIVED_COMPLETE','RECEIVED_DISCREPANCY','CANCELLED','REJECTED')),
    actor_id BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    preparation_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(transfer_id,sequence_no),
    CHECK(NOT (to_status='REJECTED' OR (to_status='CANCELLED' AND from_status='APPROVED')) OR (reason IS NOT NULL AND btrim(reason)<>''))
);
CREATE TABLE logistics_route (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_route PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK(code=btrim(code) AND code<>''),
    origin_branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    destination_branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    priority VARCHAR(8) NOT NULL CHECK(priority IN ('HIGH','MEDIUM','LOW')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(origin_branch_id,destination_branch_id), CHECK(origin_branch_id<>destination_branch_id)
);
CREATE TABLE logistics_dispatch (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_dispatch PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    transfer_id BIGINT NOT NULL UNIQUE REFERENCES transfers_transfer(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    route_id BIGINT NOT NULL REFERENCES logistics_route(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    carrier_text VARCHAR(160) NOT NULL CHECK(btrim(carrier_text)<>''),
    dispatched_at TIMESTAMPTZ NOT NULL,
    estimated_arrival_at TIMESTAMPTZ NOT NULL CHECK(estimated_arrival_at>=dispatched_at),
    dispatched_by BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    route_priority_snapshot VARCHAR(8) NOT NULL CHECK(route_priority_snapshot IN ('HIGH','MEDIUM','LOW')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE logistics_dispatch_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_dispatch_line PRIMARY KEY CHECK (id > 0),
    dispatch_id BIGINT NOT NULL REFERENCES logistics_dispatch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    transfer_line_id BIGINT NOT NULL UNIQUE REFERENCES transfers_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    dispatched_commercial_qty NUMERIC(18,3) NOT NULL CHECK(dispatched_commercial_qty>=0 AND dispatched_commercial_qty<>'NaN'::numeric),
    dispatched_base_qty NUMERIC(18,3) NOT NULL CHECK(dispatched_base_qty>=0 AND dispatched_base_qty<>'NaN'::numeric),
    frozen_base_cost NUMERIC(33,6) NOT NULL CHECK(frozen_base_cost<>'NaN'::numeric),
    base_value NUMERIC(60,18) NOT NULL CHECK(base_value<>'NaN'::numeric),
    rounding_value NUMERIC(60,18) NOT NULL DEFAULT 0 CHECK(rounding_value<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(base_value=dispatched_base_qty*frozen_base_cost),
    CHECK(dispatched_base_qty<>0 OR (base_value=0 AND rounding_value=0))
);
CREATE TABLE logistics_arrival (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_arrival PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    dispatch_id BIGINT NOT NULL UNIQUE REFERENCES logistics_dispatch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    arrived_at TIMESTAMPTZ NOT NULL,
    confirmed_by BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE logistics_arrival_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_arrival_line PRIMARY KEY CHECK (id > 0),
    arrival_id BIGINT NOT NULL REFERENCES logistics_arrival(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    dispatch_line_id BIGINT NOT NULL UNIQUE REFERENCES logistics_dispatch_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    received_commercial_qty NUMERIC(18,3) NOT NULL CHECK(received_commercial_qty>=0 AND received_commercial_qty<>'NaN'::numeric),
    received_base_qty NUMERIC(18,3) NOT NULL CHECK(received_base_qty>=0 AND received_base_qty<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE logistics_discrepancy (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_discrepancy PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK(external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    arrival_id BIGINT NOT NULL UNIQUE REFERENCES logistics_arrival(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    opened_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),
    treatment VARCHAR(12) CHECK(treatment IN ('RESEND','ADJUSTMENT','CLAIM')),
    resolved_by BIGINT REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    resolved_at TIMESTAMPTZ CHECK(resolved_at>=opened_at),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((status='OPEN' AND num_nonnulls(treatment,resolved_by,resolved_at)=0)
        OR (status='RESOLVED' AND num_nonnulls(treatment,resolved_by,resolved_at)=3))
);
ALTER TABLE transfers_transfer ADD CONSTRAINT fk_transfers_source_discrepancy
    FOREIGN KEY(source_discrepancy_id) REFERENCES logistics_discrepancy(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE TABLE logistics_discrepancy_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_logistics_discrepancy_line PRIMARY KEY CHECK (id > 0),
    discrepancy_id BIGINT NOT NULL REFERENCES logistics_discrepancy(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    arrival_line_id BIGINT NOT NULL UNIQUE REFERENCES logistics_arrival_line(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    missing_base_qty NUMERIC(18,3) NOT NULL CHECK(missing_base_qty>0 AND missing_base_qty<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
