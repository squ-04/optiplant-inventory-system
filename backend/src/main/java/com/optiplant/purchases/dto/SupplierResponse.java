package com.optiplant.purchases.dto;

import java.util.UUID;

/** Public representation of a supplier, identified by its external id rather than its database key. */
public record SupplierResponse(UUID externalId, String code, String name, boolean active) {}
