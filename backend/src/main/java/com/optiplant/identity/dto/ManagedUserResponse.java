package com.optiplant.identity.dto;

import java.util.UUID;

public record ManagedUserResponse(UUID externalId, String code, String fullName, String role,
                                  UUID branchExternalId, boolean active, boolean mustChangePassword) {}
