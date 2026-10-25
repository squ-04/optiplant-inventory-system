package com.optiplant.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VisibilityResponse(Branch branch, List<InventoryItem> inventory, List<Document> purchases,
    List<Document> sales, List<Document> transfers, List<Movement> movements) {
    public record Branch(UUID externalId, String code, String name, boolean active) {}
    public record InventoryItem(UUID productExternalId, String sku, String name, String physicalQuantity) {}
    public record Document(UUID externalId, String code, String status, Instant occurredAt) {}
    public record Movement(UUID externalId, Instant occurredAt, String kind, String direction,
        String sku, String productName, String quantity, String unit, String actorCode) {}
}
