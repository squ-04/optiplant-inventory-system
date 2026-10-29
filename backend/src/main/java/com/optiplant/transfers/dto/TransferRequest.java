package com.optiplant.transfers.dto;

import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A transfer request of the session's own branch, the destination, from another branch, the origin (RF-0032). Each line
 * names a product, one of its commercial units and a positive quantity with up to 3 decimals; a product/unit appears
 * once (INC-015/D02). There is no urgency or priority field (INC-019/D03).
 */
public record TransferRequest(UUID origin, List<Line> lines) {
    public static final int MAX_LINES = 100;
    private static final Set<String> FIELDS = Set.of("origin", "lines");
    private static final Set<String> LINE_FIELDS = Set.of("product", "unit", "quantity");

    public record Line(UUID product, String unit, BigDecimal quantity) {}

    public static TransferRequest from(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) throw invalid("Campo no admitido: " + name + ". La solicitud solo indica el origen y los productos.");
        });
        UUID origin = TransferText.uuid(body.path("origin"), "Seleccione la sucursal origen.");
        JsonNode lines = body.path("lines");
        if (!lines.isArray() || lines.isEmpty()) throw invalid("La solicitud debe incluir al menos un producto.");
        if (lines.size() > MAX_LINES) throw invalid("La solicitud admite hasta " + MAX_LINES + " líneas.");
        List<Line> parsed = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Line line = line(lines.get(index), index + 1);
            for (int previous = 0; previous < parsed.size(); previous++) {
                if (parsed.get(previous).product().equals(line.product()) && parsed.get(previous).unit().equals(line.unit())) {
                    throw invalid("Línea " + (index + 1) + ": repite el producto y la unidad de la línea " + (previous + 1)
                        + ". Indique la cantidad total en una sola línea.");
                }
            }
            parsed.add(line);
        }
        return new TransferRequest(origin, List.copyOf(parsed));
    }

    private static Line line(JsonNode line, int number) {
        String prefix = "Línea " + number + ": ";
        if (!line.isObject()) throw invalid(prefix + "no es válida.");
        line.propertyNames().forEach(name -> {
            if (!LINE_FIELDS.contains(name)) throw invalid(prefix + "campo no admitido: " + name + ".");
        });
        UUID product = TransferText.uuid(line.path("product"), prefix + "seleccione el producto.");
        JsonNode unit = line.path("unit");
        if (!unit.isTextual() || unit.asText().isBlank()) throw invalid(prefix + "seleccione la unidad.");
        BigDecimal quantity;
        try {
            quantity = ProductEnablementRequest.quantity(line.path("quantity"));
        } catch (ApiException error) {
            throw invalid(prefix + "la cantidad debe ser un decimal positivo con hasta 3 decimales.");
        }
        if (quantity.signum() <= 0) throw invalid(prefix + "la cantidad debe ser mayor que cero.");
        return new Line(product, unit.asText(), quantity);
    }

    static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
