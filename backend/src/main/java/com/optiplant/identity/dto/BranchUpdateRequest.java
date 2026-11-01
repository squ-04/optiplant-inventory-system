package com.optiplant.identity.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Body of a branch update. Only the name can change: the branch code is immutable after creation. */
public record BranchUpdateRequest(String name) {
    public static BranchUpdateRequest from(JsonNode body) {
        BranchCreateRequest.validateFields(body, Set.of("name"));
        return new BranchUpdateRequest(BranchCreateRequest.name(body));
    }
}
