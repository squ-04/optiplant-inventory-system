package com.optiplant.transfers.dto;

import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

/**
 * The quantities the origin confirms for every line of a transfer (RF-0033), in the requested commercial unit: from 0
 * up to the requested quantity, never more (INC-015/D03). Every line is prepared at once, so the destination always sees
 * one complete proposal.
 */
public record TransferPreparationRequest(Map<Integer, BigDecimal> quantities) {
    private static final Set<String> FIELDS = Set.of("lines");
    private static final Set<String> LINE_FIELDS = Set.of("lineNo", "quantity");

    public static TransferPreparationRequest from(JsonNode body) {
        if (body == null || !body.isObject()) throw TransferRequest.invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!FIELDS.contains(name)) throw TransferRequest.invalid("Campo no admitido: " + name + ". La preparación solo indica las cantidades por línea.");
        });
        JsonNode lines = body.path("lines");
        if (!lines.isArray() || lines.isEmpty()) throw TransferRequest.invalid("Indique la cantidad preparada de cada línea.");
        if (lines.size() > TransferRequest.MAX_LINES) throw TransferRequest.invalid("La transferencia admite hasta " + TransferRequest.MAX_LINES + " líneas.");
        Map<Integer, BigDecimal> quantities = new LinkedHashMap<>();
        for (JsonNode line : lines) {
            if (!line.isObject()) throw TransferRequest.invalid("Cada línea preparada debe indicar su número y cantidad.");
            line.propertyNames().forEach(name -> {
                if (!LINE_FIELDS.contains(name)) throw TransferRequest.invalid("Campo de línea no admitido: " + name + ".");
            });
            JsonNode number = line.path("lineNo");
            if (!number.isIntegralNumber() || !number.canConvertToInt() || number.asInt() < 1) {
                throw TransferRequest.invalid("Cada línea preparada debe indicar su número de línea.");
            }
            int lineNo = number.asInt();
            BigDecimal quantity;
            try {
                quantity = ProductEnablementRequest.quantity(line.path("quantity"));
            } catch (ApiException error) {
                throw TransferRequest.invalid("Línea " + lineNo + ": la cantidad preparada debe ser un decimal de 0 o más con hasta 3 decimales.");
            }
            if (quantities.put(lineNo, quantity) != null) {
                throw TransferRequest.invalid("Línea " + lineNo + ": se indicó más de una vez.");
            }
        }
        // Sorted, so the same quantities always hash to the same idempotent request.
        return new TransferPreparationRequest(Collections.unmodifiableSortedMap(new TreeMap<>(quantities)));
    }
}
