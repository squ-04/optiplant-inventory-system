-- RF-0032–RF-0034: the state history of a transfer is append-only; the transfer and its lines keep their mutable state.
REVOKE UPDATE ON transfers_event FROM optiplant_runtime;
