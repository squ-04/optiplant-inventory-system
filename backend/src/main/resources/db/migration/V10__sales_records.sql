-- RF-0031 / INC-014/D06: a confirmed sale record is immutable. The runtime role keeps SELECT and INSERT only on sales
-- and their lines (DELETE was never granted); a return is a new linked inventory movement, never an edit.
REVOKE UPDATE ON sales_sale, sales_line FROM optiplant_runtime;

-- The business code of a sale is assigned on insert, so the record never needs a later update.
CREATE SEQUENCE sales_sale_code_seq AS BIGINT MINVALUE 1;
GRANT USAGE, SELECT ON SEQUENCE sales_sale_code_seq TO optiplant_runtime;
