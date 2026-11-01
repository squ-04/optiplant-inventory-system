package com.optiplant.identity.dto;

import java.util.UUID;

/** The session user as the client needs it: identity, role, branch scope and whether a password change is pending. */
public record UserResponse(UUID externalId, String code, String role, UUID branchExternalId,
                           boolean mustChangePassword) {}
