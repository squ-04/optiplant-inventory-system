package com.optiplant.catalog.dto;

import com.optiplant.identity.dto.BranchCreateRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record ProductCreateRequest(ProductUpdateRequest attributes, List<ProductEnablementRequest> branches) {
    public static ProductCreateRequest from(JsonNode body) {
        Set<String> fields = new HashSet<>(ProductUpdateRequest.FIELDS);
        fields.add("branches");
        BranchCreateRequest.validateFields(body, fields);
        ProductUpdateRequest attributes = ProductUpdateRequest.attributes(body);
        JsonNode values = body.path("branches");
        if (!values.isArray()) throw ProductUpdateRequest.invalid();
        List<ProductEnablementRequest> branches = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (JsonNode value : values) {
            ProductEnablementRequest branch = ProductEnablementRequest.from(value);
            if (!seen.add(branch.branchExternalId()) || !branch.admits(attributes.baseDivisible())) {
                throw ProductUpdateRequest.invalid();
            }
            branches.add(branch);
        }
        return new ProductCreateRequest(attributes, List.copyOf(branches));
    }
}
