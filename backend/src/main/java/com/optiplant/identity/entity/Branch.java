package com.optiplant.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_branch")
public class Branch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID externalId;
    @Column(nullable = false, length = 64, updatable = false)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Branch() {}

    public static Branch create(String code, String name, Instant now) {
        Branch branch = new Branch();
        branch.externalId = UUID.randomUUID();
        branch.code = code;
        branch.name = name;
        branch.active = true;
        branch.createdAt = now;
        branch.updatedAt = now;
        return branch;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
