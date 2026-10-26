package com.optiplant.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Global catalog product. Products are never physically deleted; removal is {@code active=false} only. */
@Entity
@Table(name = "catalog_product")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID externalId;
    @Column(nullable = false, unique = true, length = 64)
    private String sku;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false, length = 64)
    private String baseUnitName;
    @Column(nullable = false)
    private boolean baseDivisible;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public static Product create(String sku, String name, String description, String baseUnitName, boolean baseDivisible,
        Instant now) {
        Product product = new Product();
        product.externalId = UUID.randomUUID();
        product.sku = sku;
        product.name = name;
        product.description = description;
        product.baseUnitName = baseUnitName;
        product.baseDivisible = baseDivisible;
        product.active = true;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    public void update(String sku, String name, String description, String baseUnitName, boolean baseDivisible, Instant now) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.baseUnitName = baseUnitName;
        this.baseDivisible = baseDivisible;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public void reactivate(Instant now) {
        active = true;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBaseUnitName() { return baseUnitName; }
    public boolean isBaseDivisible() { return baseDivisible; }
    public boolean isActive() { return active; }
}
