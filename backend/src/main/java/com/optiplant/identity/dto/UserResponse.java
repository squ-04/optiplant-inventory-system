package com.optiplant.identity.dto;

import java.util.UUID;

public record UserResponse(UUID externalId, String code, String role, UUID branchExternalId,
                           boolean mustChangePassword) {}
