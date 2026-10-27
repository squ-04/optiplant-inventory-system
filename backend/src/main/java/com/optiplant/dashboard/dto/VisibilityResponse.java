package com.optiplant.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VisibilityResponse(Branch branch, List<InventoryItem> inventory, List<Document> purchases,
    List<Document> sales, List<Document> transfers, List<Movement> movements) {
    public record Branch(UUID externalId, String code, String name, boolean active) {}
    public record InventoryItem(UUID productExternalId, String sku, String name, String physicalQuantity) {}
    /**
     * Stock of one branch consulted from any branch (RF-0014/RF-0015). Quantities are exact base-unit decimal strings;
     * {@code available} equals physical (no reservations, glossary §5.3) and {@code inTransit} is dispatched towards the
     * branch, neither physical nor available. {@code readAt} is the database snapshot time of the read.
     */
    public record BranchInventory(Branch branch, Instant readAt, List<StockItem> items) {}
    public record StockItem(UUID productExternalId, String sku, String name, String baseUnitName,
        String physicalQuantity, String availableQuantity, String inTransitQuantity) {}
    public record Document(UUID externalId, String code, String status, Instant occurredAt) {}
    public record Movement(UUID externalId, Instant occurredAt, String kind, String direction,
        String sku, String productName, String quantity, String unit, String actorCode) {}
}
