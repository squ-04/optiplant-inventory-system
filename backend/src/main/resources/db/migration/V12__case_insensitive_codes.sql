-- H10 (E2E 2026-10-29): the codes people type to identify master data are unique regardless of letter case, so
-- «E2E-NORTE» and «e2e-norte» can no longer name two different records. Codes keep the spelling they were typed with.
-- Generated codes (OC-, VT-, TR-) are always upper case and keep their exact constraints.
--
-- Existing data is never renamed, merged or deleted here: if codes already collide, the migration stops before any
-- change and lists them, so the responsible decides which record to rename and starts the application again.
DO $$
DECLARE
    collisions TEXT;
BEGIN
    SELECT string_agg(format('%s: %s', source, codes), '; ' ORDER BY source, codes) INTO collisions FROM (
        SELECT 'identity_branch' AS source, string_agg(code, ', ' ORDER BY code COLLATE "C") AS codes
            FROM identity_branch GROUP BY lower(code) HAVING count(*) > 1
        UNION ALL SELECT 'identity_user_account', string_agg(code, ', ' ORDER BY code COLLATE "C")
            FROM identity_user_account GROUP BY lower(code) HAVING count(*) > 1
        UNION ALL SELECT 'catalog_product', string_agg(sku, ', ' ORDER BY sku COLLATE "C")
            FROM catalog_product GROUP BY lower(sku) HAVING count(*) > 1
        UNION ALL SELECT 'catalog_price_list', string_agg(code, ', ' ORDER BY code COLLATE "C")
            FROM catalog_price_list GROUP BY lower(code) HAVING count(*) > 1
        UNION ALL SELECT 'purchases_supplier', string_agg(code, ', ' ORDER BY code COLLATE "C")
            FROM purchases_supplier GROUP BY lower(code) HAVING count(*) > 1
        UNION ALL SELECT 'logistics_route', string_agg(code, ', ' ORDER BY code COLLATE "C")
            FROM logistics_route GROUP BY lower(code) HAVING count(*) > 1
    ) found;
    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION 'Hay códigos que solo difieren en mayúsculas y minúsculas: %. No se modificó ningún dato: '
            'renombre uno de cada grupo y vuelva a iniciar la aplicación.', collisions
            USING ERRCODE = 'unique_violation';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_identity_branch_code_ci ON identity_branch (lower(code));
CREATE UNIQUE INDEX uq_identity_user_code_ci ON identity_user_account (lower(code));
CREATE UNIQUE INDEX uq_catalog_product_sku_ci ON catalog_product (lower(sku));
CREATE UNIQUE INDEX uq_catalog_price_list_code_ci ON catalog_price_list (lower(code));
CREATE UNIQUE INDEX uq_purchases_supplier_code_ci ON purchases_supplier (lower(code));
CREATE UNIQUE INDEX uq_logistics_route_code_ci ON logistics_route (lower(code));
