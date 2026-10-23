CREATE TABLE catalog_product (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_catalog_product PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    sku VARCHAR(64) NOT NULL UNIQUE CHECK (sku = btrim(sku) AND sku <> ''),
    name VARCHAR(160) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT,
    base_unit_name VARCHAR(64) NOT NULL CHECK (btrim(base_unit_name) <> ''),
    base_divisible BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE catalog_product_unit (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_catalog_product_unit PRIMARY KEY CHECK (id > 0),
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    name VARCHAR(64) NOT NULL CHECK (btrim(name) <> ''),
    factor NUMERIC(24,9) NOT NULL CHECK (factor > 0 AND factor <> 'NaN'::numeric),
    divisible BOOLEAN NOT NULL,
    is_base BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id,name), UNIQUE(id,product_id), CHECK (NOT is_base OR factor=1)
);
CREATE UNIQUE INDEX uq_catalog_unit_base ON catalog_product_unit(product_id) WHERE is_base;
CREATE TABLE catalog_branch_product (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_catalog_branch_product PRIMARY KEY CHECK (id > 0),
    branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    enabled BOOLEAN NOT NULL,
    minimum_qty NUMERIC(18,3) CHECK (minimum_qty >= 0 AND minimum_qty <> 'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(branch_id,product_id), CHECK (NOT enabled OR minimum_qty IS NOT NULL)
);
CREATE INDEX ix_catalog_branch_product_enabled ON catalog_branch_product(product_id,enabled);
CREATE TABLE catalog_price_list (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_catalog_price_list PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code=btrim(code) AND code<>''),
    name VARCHAR(160) NOT NULL CHECK (btrim(name)<>''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE catalog_price_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_catalog_price_entry PRIMARY KEY CHECK (id > 0),
    price_list_id BIGINT NOT NULL REFERENCES catalog_price_list(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_unit_id BIGINT NOT NULL REFERENCES catalog_product_unit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    gross_price NUMERIC(24,6) NOT NULL CHECK (gross_price>=0 AND gross_price<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(price_list_id,product_unit_id)
);
CREATE INDEX ix_catalog_price_unit ON catalog_price_entry(product_unit_id);
CREATE TABLE purchases_supplier (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_purchases_supplier PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code=btrim(code) AND code<>''),
    name VARCHAR(160) NOT NULL CHECK (btrim(name)<>''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE purchases_order (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_purchases_order PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code=btrim(code) AND code<>''),
    branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    supplier_id BIGINT NOT NULL REFERENCES purchases_supplier(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    supplier_name_snapshot VARCHAR(160) NOT NULL CHECK (btrim(supplier_name_snapshot)<>''),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PENDING','RECEIVED','CANCELLED')),
    created_by BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    payment_term_days INTEGER NOT NULL CHECK (payment_term_days>=0),
    received_at TIMESTAMPTZ,
    received_by BIGINT REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    due_date DATE,
    replaces_order_id BIGINT REFERENCES purchases_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status='RECEIVED' AND num_nonnulls(received_at,received_by,due_date)=3)
        OR (status<>'RECEIVED' AND num_nonnulls(received_at,received_by,due_date)=0)),
    CHECK (replaces_order_id<>id)
);
CREATE TABLE purchases_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_purchases_line PRIMARY KEY CHECK (id > 0),
    order_id BIGINT NOT NULL REFERENCES purchases_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    line_no INTEGER NOT NULL CHECK (line_no>0),
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_unit_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL CHECK (btrim(sku_snapshot)<>''),
    product_name_snapshot VARCHAR(160) NOT NULL CHECK (btrim(product_name_snapshot)<>''),
    unit_name_snapshot VARCHAR(64) NOT NULL CHECK (btrim(unit_name_snapshot)<>''),
    factor_snapshot NUMERIC(24,9) NOT NULL CHECK (factor_snapshot>0 AND factor_snapshot<>'NaN'::numeric),
    commercial_qty NUMERIC(18,3) NOT NULL CHECK (commercial_qty>0 AND commercial_qty<>'NaN'::numeric),
    base_qty NUMERIC(18,3) NOT NULL CHECK (base_qty>0 AND base_qty<>'NaN'::numeric),
    gross_unit_price NUMERIC(24,6) NOT NULL CHECK (gross_unit_price>=0 AND gross_unit_price<>'NaN'::numeric),
    discount_pct NUMERIC(9,6) NOT NULL CHECK (discount_pct BETWEEN 0 AND 100),
    net_amount NUMERIC(51,17) NOT NULL CHECK (net_amount>=0 AND net_amount<>'NaN'::numeric),
    historical_base_cost NUMERIC(33,6) CHECK (historical_base_cost>=0 AND historical_base_cost<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id,line_no),
    FOREIGN KEY(product_unit_id,product_id) REFERENCES catalog_product_unit(id,product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CHECK(base_qty=commercial_qty*factor_snapshot),
    CHECK(net_amount=commercial_qty*gross_unit_price*(1-discount_pct/100))
);
CREATE TABLE purchases_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_purchases_event PRIMARY KEY CHECK (id > 0),
    order_id BIGINT NOT NULL REFERENCES purchases_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    sequence_no INTEGER NOT NULL CHECK (sequence_no>0),
    from_status VARCHAR(16) CHECK (from_status IN ('DRAFT','PENDING','RECEIVED','CANCELLED')),
    to_status VARCHAR(16) NOT NULL CHECK (to_status IN ('DRAFT','PENDING','RECEIVED','CANCELLED')),
    actor_id BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id,sequence_no)
);
CREATE TABLE sales_sale (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_sales_sale PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL UNIQUE CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code=btrim(code) AND code<>''),
    branch_id BIGINT NOT NULL REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    confirmed_by BIGINT NOT NULL REFERENCES identity_user_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE sales_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_sales_line PRIMARY KEY CHECK (id > 0),
    sale_id BIGINT NOT NULL REFERENCES sales_sale(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    line_no INTEGER NOT NULL CHECK (line_no>0),
    product_id BIGINT NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    product_unit_id BIGINT NOT NULL,
    price_list_id BIGINT NOT NULL REFERENCES catalog_price_list(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    list_name_snapshot VARCHAR(160) NOT NULL CHECK (btrim(list_name_snapshot)<>''),
    sku_snapshot VARCHAR(64) NOT NULL CHECK (btrim(sku_snapshot)<>''),
    product_name_snapshot VARCHAR(160) NOT NULL CHECK (btrim(product_name_snapshot)<>''),
    unit_name_snapshot VARCHAR(64) NOT NULL CHECK (btrim(unit_name_snapshot)<>''),
    factor_snapshot NUMERIC(24,9) NOT NULL CHECK (factor_snapshot>0 AND factor_snapshot<>'NaN'::numeric),
    commercial_qty NUMERIC(18,3) NOT NULL CHECK (commercial_qty>0 AND commercial_qty<>'NaN'::numeric),
    base_qty NUMERIC(18,3) NOT NULL CHECK (base_qty>0 AND base_qty<>'NaN'::numeric),
    gross_unit_price NUMERIC(24,6) NOT NULL CHECK (gross_unit_price>=0 AND gross_unit_price<>'NaN'::numeric),
    discount_pct NUMERIC(9,6) NOT NULL CHECK (discount_pct BETWEEN 0 AND 100),
    net_amount NUMERIC(51,17) NOT NULL CHECK (net_amount>=0 AND net_amount<>'NaN'::numeric),
    historical_base_cost NUMERIC(33,6) NOT NULL CHECK (historical_base_cost<>'NaN'::numeric),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(sale_id,line_no),
    FOREIGN KEY(product_unit_id,product_id) REFERENCES catalog_product_unit(id,product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CHECK(base_qty=commercial_qty*factor_snapshot),
    CHECK(net_amount=commercial_qty*gross_unit_price*(1-discount_pct/100))
);
