package com.optiplant.identity.dto;

import java.util.UUID;

/** User as seen by administrators. It never includes the password hash or the credentials version. */
public record ManagedUserResponse(UUID externalId, String code, String fullName, String role,
                                  UUID branchExternalId, boolean active, boolean mustChangePassword) {}
