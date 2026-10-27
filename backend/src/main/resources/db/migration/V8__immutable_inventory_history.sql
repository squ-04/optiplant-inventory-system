-- RF-0018 / INC-006/D04: confirmed operations, movements and valuation components are never edited or deleted;
-- corrections are new linked records. The runtime role keeps SELECT and INSERT only (DELETE was never granted).
REVOKE UPDATE ON inventory_operation, inventory_movement, inventory_valuation_component FROM optiplant_runtime;
