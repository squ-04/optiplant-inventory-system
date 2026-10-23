-- Datos exclusivamente demostrativos, sin contraseñas ni cuentas utilizables.
-- UUIDv4 fijos identifican fixtures, no generan identidades de aplicación.
INSERT INTO identity_branch(external_id,code,name) VALUES
('d0000000-0000-4000-8000-000000000001','DEMO-BOG','Demostración Bogotá'),
('d0000000-0000-4000-8000-000000000002','DEMO-MED','Demostración Medellín');

INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,active)
SELECT 'd1000000-0000-4000-8000-000000000001','DEMO-MANAGER','Responsable histórico de demostración',
'!','MANAGER',id,FALSE FROM identity_branch WHERE code='DEMO-BOG';
INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,active)
SELECT 'd1000000-0000-4000-8000-000000000002','DEMO-OPERATOR','Operador histórico de demostración',
'!','OPERATOR',id,FALSE FROM identity_branch WHERE code='DEMO-BOG';

INSERT INTO catalog_product(external_id,sku,name,description,base_unit_name,base_divisible)
VALUES ('d2000000-0000-4000-8000-000000000001','DEMO-PLANT','Producto de demostración',
'Datos sintéticos; no representan operaciones productivas.','unidad',FALSE);
INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base)
SELECT id,'unidad',1,FALSE,TRUE FROM catalog_product WHERE sku='DEMO-PLANT';
INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty)
SELECT b.id,p.id,TRUE,2 FROM identity_branch b CROSS JOIN catalog_product p
WHERE b.code IN ('DEMO-BOG','DEMO-MED') AND p.sku='DEMO-PLANT';
INSERT INTO catalog_price_list(external_id,code,name)
VALUES ('d3000000-0000-4000-8000-000000000001','DEMO-RETAIL','Lista de demostración COP');
INSERT INTO catalog_price_entry(price_list_id,product_unit_id,gross_price)
SELECT l.id,u.id,15 FROM catalog_price_list l CROSS JOIN catalog_product_unit u
JOIN catalog_product p ON p.id=u.product_id WHERE l.code='DEMO-RETAIL' AND p.sku='DEMO-PLANT';

INSERT INTO inventory_operation(external_id,kind,occurred_at,actor_id,reason)
SELECT 'd4000000-0000-4000-8000-000000000001','MANUAL_ENTRY',CURRENT_TIMESTAMP,u.id,
'Apertura sintética de demostración Bogotá' FROM identity_user_account u WHERE code='DEMO-MANAGER';
INSERT INTO inventory_movement(external_id,operation_id,branch_id,product_id,product_unit_id,direction,
commercial_qty,base_qty,factor_snapshot,sku_snapshot,product_name_snapshot,unit_name_snapshot,historical_base_cost)
SELECT 'd5000000-0000-4000-8000-000000000001',o.id,b.id,p.id,u.id,'IN',10,10,1,p.sku,p.name,u.name,10
FROM inventory_operation o CROSS JOIN identity_branch b CROSS JOIN catalog_product p
JOIN catalog_product_unit u ON u.product_id=p.id AND u.is_base
WHERE o.external_id='d4000000-0000-4000-8000-000000000001' AND b.code='DEMO-BOG' AND p.sku='DEMO-PLANT';
INSERT INTO inventory_valuation_component(movement_id,component_kind,amount)
SELECT id,'BASE',100 FROM inventory_movement WHERE external_id='d5000000-0000-4000-8000-000000000001';

INSERT INTO sales_sale(external_id,code,branch_id,confirmed_by,confirmed_at)
SELECT 'd6000000-0000-4000-8000-000000000001','DEMO-SALE-001',b.id,u.id,CURRENT_TIMESTAMP
FROM identity_branch b CROSS JOIN identity_user_account u WHERE b.code='DEMO-BOG' AND u.code='DEMO-OPERATOR';
INSERT INTO sales_line(sale_id,line_no,product_id,product_unit_id,price_list_id,list_name_snapshot,
sku_snapshot,product_name_snapshot,unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,
gross_unit_price,discount_pct,net_amount,historical_base_cost)
SELECT s.id,1,p.id,u.id,l.id,l.name,p.sku,p.name,u.name,1,2,2,15,0,30,10
FROM sales_sale s CROSS JOIN catalog_product p JOIN catalog_product_unit u ON u.product_id=p.id AND u.is_base
CROSS JOIN catalog_price_list l WHERE s.code='DEMO-SALE-001' AND p.sku='DEMO-PLANT' AND l.code='DEMO-RETAIL';
INSERT INTO inventory_operation(external_id,kind,occurred_at,actor_id,reason,sale_id)
SELECT 'd4000000-0000-4000-8000-000000000002','SALE',confirmed_at,confirmed_by,'Venta sintética de demostración',id
FROM sales_sale WHERE code='DEMO-SALE-001';
INSERT INTO inventory_movement(external_id,operation_id,branch_id,product_id,product_unit_id,direction,
commercial_qty,base_qty,factor_snapshot,sku_snapshot,product_name_snapshot,unit_name_snapshot,historical_base_cost,sale_line_id)
SELECT 'd5000000-0000-4000-8000-000000000002',o.id,s.branch_id,l.product_id,l.product_unit_id,'OUT',
l.commercial_qty,l.base_qty,l.factor_snapshot,l.sku_snapshot,l.product_name_snapshot,l.unit_name_snapshot,l.historical_base_cost,l.id
FROM inventory_operation o JOIN sales_sale s ON s.id=o.sale_id JOIN sales_line l ON l.sale_id=s.id
WHERE o.external_id='d4000000-0000-4000-8000-000000000002';
INSERT INTO inventory_valuation_component(movement_id,component_kind,amount)
SELECT id,'BASE',-20 FROM inventory_movement WHERE external_id='d5000000-0000-4000-8000-000000000002';
INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at)
SELECT b.id,p.id,8,80,10,o.occurred_at FROM identity_branch b CROSS JOIN catalog_product p CROSS JOIN inventory_operation o
WHERE b.code='DEMO-BOG' AND p.sku='DEMO-PLANT' AND o.external_id='d4000000-0000-4000-8000-000000000001';
