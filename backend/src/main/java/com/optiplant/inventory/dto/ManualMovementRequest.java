package com.optiplant.inventory.dto;

import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.common.exception.ApiException;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A manual entry or exit registered in the session's own branch (RF-0016/RF-0017). Quantities and costs are exact
 * decimal strings. {@code product} and {@code unit} identify what moves, except for a referenced return or a
 * correction, which take product, unit and historical cost from the referenced movement.
 */
public record ManualMovementRequest(Kind kind, Direction direction, UUID product, String unit, BigDecimal quantity,
                                    BigDecimal unitCost, UUID reference, String reason) {
    public static final int MAX_REASON = 500;
    /** Kinds a person registers manually; documents (purchases, sales, transfers) post their own operations. */
    public static final Set<Kind> MANUAL = Set.of(Kind.MANUAL_ENTRY, Kind.SALE_RETURN, Kind.MANUAL_EXIT,
        Kind.COUNT_ADJUSTMENT, Kind.ENTRY_CORRECTION);
    // A correction only removes units registered in excess (decision L14); missing units enter as a new movement.
    private static final Map<Kind, Direction> FIXED = Map.of(Kind.MANUAL_ENTRY, Direction.IN, Kind.SALE_RETURN, Direction.IN,
        Kind.MANUAL_EXIT, Direction.OUT, Kind.ENTRY_CORRECTION, Direction.OUT);
    private static final Set<String> FIELDS = Set.of("kind", "direction", "product", "unit", "quantity", "unitCost",
        "reference", "reason");
    // inventory_movement.historical_base_cost is NUMERIC(33,6).
    private static final Pattern COST = Pattern.compile("^\\d{1,27}(\\.\\d{1,6})?$");

    public static ManualMovementRequest from(JsonNode body) {
        if (!body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> { if (!FIELDS.contains(name)) throw invalid("Campo no admitido: " + name + "."); });
        Kind kind = kind(body.path("kind"));
        Direction direction = direction(kind, optionalText(body, "direction"));
        UUID reference = uuid(optionalText(body, "reference"));
        UUID product = uuid(optionalText(body, "product"));
        String unit = optionalText(body, "unit");
        boolean referenced = kind == Kind.ENTRY_CORRECTION || (kind == Kind.SALE_RETURN && reference != null);
        if (kind == Kind.ENTRY_CORRECTION && reference == null) {
            throw invalid("La corrección debe referenciar la entrada identificada que corrige.");
        }
        if (reference != null && !referenced) throw invalid("Este tipo de movimiento no admite referencia.");
        if (referenced && (product != null || unit != null)) {
            throw invalid("Producto y unidad se toman del movimiento referenciado; no los indique.");
        }
        if (!referenced && (product == null || unit == null || unit.isBlank())) throw invalid("Indique producto y unidad.");
        String cost = optionalText(body, "unitCost");
        BigDecimal unitCost = null;
        if (cost != null) {
            boolean unreferencedEntry = kind == Kind.MANUAL_ENTRY || (kind == Kind.SALE_RETURN && reference == null)
                || (kind == Kind.COUNT_ADJUSTMENT && direction == Direction.IN);
            if (!unreferencedEntry) throw new ApiException(400, "UNIT_COST_NOT_ALLOWED",
                "Este movimiento se valora al promedio vigente o al costo histórico referenciado; no indique un costo.");
            if (!COST.matcher(cost).matches()) throw invalid("El costo unitario debe ser un decimal no negativo con hasta 6 decimales.");
            unitCost = new BigDecimal(cost);
        }
        JsonNode quantityNode = body.path("quantity");
        BigDecimal quantity;
        try {
            quantity = ProductEnablementRequest.quantity(quantityNode);
        } catch (ApiException error) {
            throw invalid("La cantidad debe ser un decimal positivo con hasta 3 decimales.");
        }
        if (quantity.signum() <= 0) throw invalid("La cantidad debe ser mayor que cero.");
        String reason = optionalText(body, "reason");
        if (reason == null || reason.isBlank()) throw invalid("El motivo es obligatorio.");
        if (reason.length() > MAX_REASON) throw invalid("El motivo admite hasta 500 caracteres.");
        return new ManualMovementRequest(kind, direction, product, unit, quantity, unitCost, reference, reason.strip());
    }

    private static Kind kind(JsonNode value) {
        try {
            Kind kind = Kind.valueOf(value.asText(""));
            if (value.isTextual() && MANUAL.contains(kind)) return kind;
        } catch (IllegalArgumentException ignored) { /* reported below */ }
        throw invalid("Tipo de movimiento no admitido.");
    }

    private static Direction direction(Kind kind, String value) {
        Direction fixed = FIXED.get(kind);
        if (value == null) {
            if (fixed == null) throw invalid("Indique si el movimiento incrementa (IN) o disminuye (OUT) las existencias.");
            return fixed;
        }
        Direction direction;
        try {
            direction = Direction.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw invalid("La dirección debe ser IN u OUT.");
        }
        if (kind == Kind.ENTRY_CORRECTION && direction != fixed) {
            throw invalid("Una corrección solo descuenta unidades registradas de más. Registre las unidades faltantes como "
                + "un nuevo ingreso o un ajuste por conteo.");
        }
        if (fixed != null && fixed != direction) throw invalid("La dirección no corresponde al tipo de movimiento.");
        return direction;
    }

    private static String optionalText(JsonNode body, String field) {
        JsonNode value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw invalid("El campo " + field + " debe ser texto.");
        return value.asText();
    }

    private static UUID uuid(String value) {
        if (value == null) return null;
        try {
            UUID reference = UUID.fromString(value);
            if (reference.version() == 4 && reference.variant() == 2 && reference.toString().equalsIgnoreCase(value)) return reference;
        } catch (IllegalArgumentException ignored) { /* reported below */ }
        throw invalid("La referencia debe ser un UUIDv4.");
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }
}
