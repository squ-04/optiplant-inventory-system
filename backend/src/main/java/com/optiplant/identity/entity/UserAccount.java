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
@Table(name = "identity_user_account")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID externalId;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 160)
    private String fullName;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 16)
    private String role;
    private Long branchId;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private long credentialsVersion;
    @Column(nullable = false)
    private boolean mustChangePassword;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected UserAccount() {}

    public static UserAccount initialAdmin(String code, String hash, Instant now) {
        UserAccount account = new UserAccount();
        account.externalId = UUID.randomUUID();
        account.code = code;
        account.fullName = "Administrador inicial";
        account.passwordHash = hash;
        account.role = "ADMIN";
        account.active = true;
        account.mustChangePassword = true;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void changePassword(String hash, Instant now) {
        passwordHash = hash;
        mustChangePassword = false;
        credentialsVersion = Math.incrementExact(credentialsVersion);
        updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getExternalId() { return externalId; }
    public String getCode() { return code; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Long getBranchId() { return branchId; }
    public boolean isActive() { return active; }
    public long getCredentialsVersion() { return credentialsVersion; }
    public boolean isMustChangePassword() { return mustChangePassword; }
}
