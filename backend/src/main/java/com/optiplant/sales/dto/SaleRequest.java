package com.optiplant.sales.dto;

import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A sale confirmed by the operator of the session's own branch (RF-0027): the price list chosen at confirmation
 * (INC-014/D01) and its lines with product, commercial unit, quantity and a line discount between 0 and 100%
 * (RF-0029, INC-014/D02). There is no unit price: the gross price always comes from the chosen list, and there is no
 * header discount. Decimals are exact strings; nothing is rounded.
 */
public record SaleRequest(UUID priceList, List<Line> lines) {
    public static final int MAX_LINES = 100;
    private static final Set<String> FIELDS = Set.of("priceList", "lines");
    private static final Set<String> LINE_FIELDS = Set.of("product", "unit", "quantity", "discountPercent");
    // sales_line.discount_pct is NUMERIC(9,6).
    private static final Pattern PERCENT = Pattern.compile("^\\d{1,3}(\\.\\d{1,6})?$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DISCOUNT_RANGE = "el descuento debe ser un porcentaje entre 0 y 100, con hasta 6 decimales "
        + "(indique 0 si no aplica).";

    public record Line(UUID product, String unit, BigDecimal quantity, BigDecimal discountPercent) {}

    public static SaleRequest from(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) {
                throw invalid("Campo no admitido: " + name + ". La venta solo admite la lista de precios y sus líneas; "
                    + "no hay descuento de cabecera.");
            }
        });
        UUID priceList = uuid(body.path("priceList"), "Seleccione la lista de precios de la venta.");
        JsonNode lines = body.path("lines");
        if (!lines.isArray() || lines.isEmpty()) throw invalid("La venta debe incluir al menos un producto.");
        if (lines.size() > MAX_LINES) throw invalid("La venta admite hasta " + MAX_LINES + " líneas.");
        List<Line> parsed = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            try {
                parsed.add(line(lines.get(index), index + 1));
            } catch (ApiException error) {
                // Every line rejection states, like the ones found when confirming, that nothing was sold.
                throw new ApiException(error.status(), error.code(), error.getMessage() + " No se confirmó la venta.");
            }
        }
        return new SaleRequest(priceList, List.copyOf(parsed));
    }

    private static Line line(JsonNode line, int number) {
        String prefix = "Línea " + number + ": ";
        if (!line.isObject()) throw invalid(prefix + "no es válida.");
        line.propertyNames().forEach(name -> {
            if (name.equals("unitPrice")) {
                throw invalid(prefix + "el precio se toma de la lista elegida y no se modifica; registre solo el descuento.");
            }
            if (!LINE_FIELDS.contains(name)) throw invalid(prefix + "campo no admitido: " + name + ".");
        });
        UUID product = uuid(line.path("product"), prefix + "seleccione el producto.");
        JsonNode unit = line.path("unit");
        if (!unit.isTextual() || unit.asText().isBlank()) throw invalid(prefix + "seleccione la unidad.");
        BigDecimal quantity;
        try {
            quantity = ProductEnablementRequest.quantity(line.path("quantity"));
        } catch (ApiException error) {
            throw invalid(prefix + "la cantidad debe ser un decimal positivo con hasta 3 decimales.");
        }
        if (quantity.signum() <= 0) throw invalid(prefix + "la cantidad debe ser mayor que cero.");
        JsonNode discount = line.path("discountPercent");
        if (!discount.isTextual() || !PERCENT.matcher(discount.asText()).matches()) throw invalid(prefix + DISCOUNT_RANGE);
        BigDecimal percent = new BigDecimal(discount.asText());
        if (percent.compareTo(HUNDRED) > 0) throw invalid(prefix + DISCOUNT_RANGE);
        return new Line(product, unit.asText(), quantity, percent);
    }

    private static UUID uuid(JsonNode value, String detail) {
        if (!value.isTextual()) throw invalid(detail);
        try {
            UUID reference = UUID.fromString(value.asText());
            if (reference.version() == 4 && reference.variant() == 2 && reference.toString().equalsIgnoreCase(value.asText())) {
                return reference;
            }
        } catch (IllegalArgumentException ignored) { /* reported below */ }
        throw invalid(detail);
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
