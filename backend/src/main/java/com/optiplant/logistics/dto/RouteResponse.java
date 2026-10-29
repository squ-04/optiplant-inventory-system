package com.optiplant.logistics.dto;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import java.util.UUID;

/** A directed route with its stable public identifier and manual priority (RF-0041). */
public record RouteResponse(UUID externalId, String code, Branch origin, Branch destination, String priority, boolean active) {}
