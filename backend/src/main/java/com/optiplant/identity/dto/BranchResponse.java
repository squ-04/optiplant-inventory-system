package com.optiplant.identity.dto;

import java.util.UUID;

/** Public representation of a branch, identified by its external id rather than its database key. */
public record BranchResponse(UUID externalId, String code, String name, boolean active) {}
