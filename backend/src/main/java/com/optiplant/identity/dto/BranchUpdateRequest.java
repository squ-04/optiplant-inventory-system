package com.optiplant.identity.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;

public record BranchUpdateRequest(String name) {
    public static BranchUpdateRequest from(JsonNode body) {
        BranchCreateRequest.validateFields(body, Set.of("name"));
        return new BranchUpdateRequest(BranchCreateRequest.name(body));
    }
}
