package com.optiplant.identity.dto;

import com.optiplant.common.exception.ApiException;
import tools.jackson.databind.JsonNode;

/** Commands without editable attributes accept no body or an empty JSON object only. */
public final class EmptyCommandBody {
    private EmptyCommandBody() {}

    public static void validate(JsonNode body) {
        if (body != null && (!body.isObject() || body.size() != 0)) {
            throw new ApiException(400, "VALIDATION_FAILED", "La solicitud no es válida.");
        }
    }
}
