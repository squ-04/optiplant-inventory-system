package com.optiplant.purchases.dto;

import java.util.UUID;

public record SupplierResponse(UUID externalId, String code, String name, boolean active) {}
