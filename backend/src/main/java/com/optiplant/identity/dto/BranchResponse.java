package com.optiplant.identity.dto;

import java.util.UUID;

public record BranchResponse(UUID externalId, String code, String name, boolean active) {}
