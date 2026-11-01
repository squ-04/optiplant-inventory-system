package com.optiplant.identity.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Body of a user creation, including the temporary password the user must replace. Unknown fields are rejected rather
 * than silently ignored; value rules are applied by {@code UserManagementService}.
 */
public class ManagedUserCreateRequest {
    private String code;
    private String fullName;
    private String role;
    private UUID branchExternalId;
    private String temporaryPassword;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public UUID getBranchExternalId() { return branchExternalId; }
    public void setBranchExternalId(UUID branchExternalId) { this.branchExternalId = branchExternalId; }
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Campo no permitido: " + field);
    }
}
