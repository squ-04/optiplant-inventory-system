package com.optiplant.purchases.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimum reusable supplier (RF-0021, INC-012/D01–D02): stable identity, unique code and name, without fiscal or
 * contact data. Orders keep their own historical supplier name, so renaming a supplier never rewrites them.
 */
@Entity
@Table(name = "purchases_supplier")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID externalId;
    @Column(nullable = false, unique = true, length = 64)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Supplier() {}

    public static Supplier create(String code, String name, Instant now) {
        Supplier supplier = new Supplier();
        supplier.externalId = UUID.randomUUID();
        supplier.code = code;
        supplier.name = name;
        supplier.active = true;
        supplier.createdAt = now;
        supplier.updatedAt = now;
        return supplier;
    }

    public void update(String code, String name, Instant now) {
        this.code = code;
        this.name = name;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
