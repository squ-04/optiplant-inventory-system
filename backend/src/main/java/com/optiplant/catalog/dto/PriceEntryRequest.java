package com.optiplant.catalog.dto;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Gross price of one commercial unit of a product in a list (RF-0030, INC-014/D01). The price is an exact COP string
 * that fits {@code catalog_price_entry.gross_price NUMERIC(24,6)}; nothing is rounded.
 */
public record PriceEntryRequest(UUID product, String unit, BigDecimal price) {
    private static final Set<String> FIELDS = Set.of("product", "unit", "price");
    private static final Pattern PRICE = Pattern.compile("^\\d{1,18}(\\.\\d{1,6})?$");

    public static PriceEntryRequest from(JsonNode body) {
        if (!body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) throw invalid("Campo no admitido: " + name + ".");
        });
        BranchCreateRequest.validateFields(body, FIELDS);
        UUID product = uuid(body.path("product"));
        JsonNode unit = body.path("unit");
        if (!unit.isTextual() || unit.asText().isBlank()) throw invalid("La unidad es obligatoria.");
        JsonNode price = body.path("price");
        if (!price.isTextual() || !PRICE.matcher(price.asText()).matches()) {
            throw invalid("El precio debe ser un número mayor o igual que cero con hasta 18 enteros y 6 decimales.");
        }
        return new PriceEntryRequest(product, unit.asText(), new BigDecimal(price.asText()));
    }

    private static UUID uuid(JsonNode value) {
        if (value.isTextual()) {
            try {
                UUID reference = UUID.fromString(value.asText());
                if (reference.version() == 4 && reference.variant() == 2 && reference.toString().equalsIgnoreCase(value.asText())) {
                    return reference;
                }
            } catch (IllegalArgumentException ignored) {
                // Reported below with the same message as any other malformed reference.
            }
        }
        throw invalid("El producto debe identificarse con un UUIDv4.");
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
