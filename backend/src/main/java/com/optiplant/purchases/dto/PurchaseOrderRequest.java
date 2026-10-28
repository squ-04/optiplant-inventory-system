package com.optiplant.purchases.dto;

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
 * A new purchase order of the session's own branch (RF-0021) with its commercial conditions (RF-0023): gross unit
 * price per commercial unit, explicit line discount between 0 and 100% and a payment term in whole days counted from
 * the confirmed receipt. Decimals are exact strings; nothing is rounded. The same body rewrites a draft (RF-0022);
 * {@code replacesOrder} is accepted only on creation and names the cancelled order the new one replaces (INC-011/D03).
 */
public record PurchaseOrderRequest(UUID supplier, int paymentTermDays, List<Line> lines, UUID replacesOrder) {
    public static final int MAX_LINES = 100;
    /** Technical limit so the due date (receipt date + term) is always representable (data model §6.3). */
    public static final int MAX_PAYMENT_TERM_DAYS = 36500;
    private static final Set<String> FIELDS = Set.of("supplier", "paymentTermDays", "lines");
    private static final Set<String> CREATE_FIELDS = Set.of("supplier", "paymentTermDays", "lines", "replacesOrder");
    private static final Set<String> LINE_FIELDS = Set.of("product", "unit", "quantity", "unitPrice", "discountPercent");
    // purchases_line.gross_unit_price is NUMERIC(24,6) and discount_pct NUMERIC(9,6).
    private static final Pattern PRICE = Pattern.compile("^\\d{1,18}(\\.\\d{1,6})?$");
    private static final Pattern PERCENT = Pattern.compile("^\\d{1,3}(\\.\\d{1,6})?$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Line(UUID product, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercent) {}

    /** A new order, optionally replacing a cancelled one. */
    public static PurchaseOrderRequest from(JsonNode body) {
        return parse(body, true);
    }

    /** The new conditions of a draft; the replaced order is fixed on creation. */
    public static PurchaseOrderRequest edit(JsonNode body) {
        return parse(body, false);
    }

    private static PurchaseOrderRequest parse(JsonNode body, boolean creation) {
        if (body == null || !body.isObject()) throw invalid("La solicitud no es válida.");
        body.propertyNames().forEach(name -> {
            if (!creation && name.equals("replacesOrder")) {
                throw invalid("La orden reemplazada se indica al crear la orden y no se modifica después.");
            }
            if (!(creation ? CREATE_FIELDS : FIELDS).contains(name)) {
                throw invalid("Campo no admitido: " + name + ". Los pagos y saldos no forman parte de la compra.");
            }
        });
        UUID supplier = uuid(body.path("supplier"), "Seleccione el proveedor de la orden.");
        int term = paymentTerm(body.path("paymentTermDays"));
        JsonNode lines = body.path("lines");
        if (!lines.isArray() || lines.isEmpty()) throw invalid("La orden debe incluir al menos un producto.");
        if (lines.size() > MAX_LINES) throw invalid("La orden admite hasta " + MAX_LINES + " líneas.");
        List<Line> parsed = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) parsed.add(line(lines.get(index), index + 1));
        JsonNode replaces = body.path("replacesOrder");
        UUID replaced = replaces.isMissingNode() || replaces.isNull() ? null
            : uuid(replaces, "La orden reemplazada debe identificarse con su referencia pública.");
        return new PurchaseOrderRequest(supplier, term, List.copyOf(parsed), replaced);
    }

    private static int paymentTerm(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0 || value.asInt() > MAX_PAYMENT_TERM_DAYS) {
            throw invalid("El plazo de pago debe ser un número entero de días entre 0 y " + MAX_PAYMENT_TERM_DAYS + ".");
        }
        return value.asInt();
    }

    private static Line line(JsonNode line, int number) {
        String prefix = "Línea " + number + ": ";
        if (!line.isObject()) throw invalid(prefix + "no es válida.");
        line.propertyNames().forEach(name -> {
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
        BigDecimal price = decimal(line.path("unitPrice"), PRICE,
            prefix + "el precio unitario debe ser un decimal no negativo con hasta 6 decimales.");
        BigDecimal discount = decimal(line.path("discountPercent"), PERCENT,
            prefix + "el descuento debe ser un porcentaje entre 0 y 100, con hasta 6 decimales (indique 0 si no aplica).");
        if (discount.compareTo(HUNDRED) > 0) {
            throw invalid(prefix + "el descuento debe ser un porcentaje entre 0 y 100, con hasta 6 decimales (indique 0 si no aplica).");
        }
        return new Line(product, unit.asText(), quantity, price, discount);
    }

    private static BigDecimal decimal(JsonNode value, Pattern pattern, String detail) {
        if (!value.isTextual() || !pattern.matcher(value.asText()).matches()) throw invalid(detail);
        return new BigDecimal(value.asText());
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
