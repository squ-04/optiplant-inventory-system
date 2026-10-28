package com.optiplant.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Global price list maintained by the administrator (RF-0030, INC-003/D06, INC-014/D01). It is never assigned to a
 * branch or customer; deactivating it withdraws it from new sales without deleting its prices.
 */
@Entity
@Table(name = "catalog_price_list")
public class PriceList {
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

    protected PriceList() {}

    public static PriceList create(String code, String name, Instant now) {
        PriceList list = new PriceList();
        list.externalId = UUID.randomUUID();
        list.code = code;
        list.name = name;
        list.active = true;
        list.createdAt = now;
        list.updatedAt = now;
        return list;
    }

    public void update(String code, String name, boolean active, Instant now) {
        this.code = code;
        this.name = name;
        this.active = active;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
