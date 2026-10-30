package com.optiplant.reports.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.dashboard.service.VisibilityService;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.inventory.dto.MovementExportRow;
import com.optiplant.inventory.service.MovementHistoryService;
import com.optiplant.logistics.service.ComplianceService;
import com.optiplant.reports.service.ReportTable.Column;
import com.optiplant.reports.service.ReportTable.Kind;
import com.optiplant.reports.service.ReportTable.Metadata;
import com.optiplant.sales.dto.SaleExportLine;
import com.optiplant.sales.service.SaleQueryService;
import com.optiplant.transfers.dto.TransferExportLine;
import com.optiplant.transfers.service.TransferQueryService;
import com.optiplant.transfers.service.TransferStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Exportable reports (RF-0050–RF-0055; UC-020). Each category only declares its columns and its reading; authorization,
 * limits, metadata and the workbook are shared. The operational modules own the queries, so a report never touches
 * balances or documents. Administrators export one branch or the network, managers their own branch, operators nothing.
 */
@Service
public class ReportService {
    public static final int MAX_ROWS = 10_000;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> MOVEMENT_KINDS = Map.of(
        "PURCHASE_RECEIPT", "Recepción de compra", "SALE", "Venta", "DISPATCH", "Despacho de transferencia",
        "ARRIVAL", "Recepción de transferencia", "MANUAL_ENTRY", "Ingreso ordinario", "MANUAL_EXIT", "Retiro o merma",
        "SALE_RETURN", "Devolución de cliente", "COUNT_ADJUSTMENT", "Ajuste por conteo", "ENTRY_CORRECTION", "Corrección de entrada",
        "TRANSIT_LOSS", "Pérdida en tránsito");
    private static final Map<String, String> TREATMENTS = Map.of("RESEND", "Reenvío", "ADJUSTMENT", "Ajuste", "CLAIM", "Reclamación");
    private static final List<Column> MOVEMENT_COLUMNS = List.of(
        new Column("ID público del movimiento", Kind.TEXT, 38), new Column("Fecha de registro", Kind.DATE_TIME, 20),
        new Column("Sucursal", Kind.TEXT, 24), new Column("SKU", Kind.TEXT, 18), new Column("Producto", Kind.TEXT, 30),
        new Column("Clase", Kind.TEXT, 26), new Column("Motivo", Kind.TEXT, 36), new Column("Cantidad", Kind.QUANTITY, 14),
        new Column("Unidad", Kind.TEXT, 14), new Column("Usuario responsable", Kind.TEXT, 28),
        new Column("Documento origen", Kind.TEXT, 30));
    private static final List<Column> SALE_COLUMNS = List.of(
        new Column("Número de venta", Kind.TEXT, 18), new Column("Fecha de confirmación", Kind.DATE_TIME, 20),
        new Column("Sucursal", Kind.TEXT, 24), new Column("Responsable", Kind.TEXT, 28), new Column("SKU", Kind.TEXT, 18),
        new Column("Producto", Kind.TEXT, 30), new Column("Unidad", Kind.TEXT, 14), new Column("Cantidad", Kind.QUANTITY, 14),
        new Column("Precio unitario (COP)", Kind.DECIMAL, 20), new Column("Descuento (%)", Kind.DECIMAL, 14),
        new Column("Importe neto de línea (COP)", Kind.MONEY, 24));
    private static final List<Column> TRANSFER_COLUMNS = List.of(
        new Column("Número de transferencia", Kind.TEXT, 22), new Column("Origen", Kind.TEXT, 24),
        new Column("Destino", Kind.TEXT, 24), new Column("Fecha de solicitud", Kind.DATE_TIME, 20),
        new Column("Fecha de despacho", Kind.DATE_TIME, 20), new Column("Fecha de recepción", Kind.DATE_TIME, 20),
        new Column("Estado", Kind.TEXT, 26), new Column("SKU", Kind.TEXT, 18), new Column("Unidad", Kind.TEXT, 14),
        new Column("Cantidad solicitada", Kind.QUANTITY, 18), new Column("Cantidad despachada", Kind.QUANTITY, 18),
        new Column("Cantidad recibida", Kind.QUANTITY, 18), new Column("Diferencia pendiente", Kind.QUANTITY, 18),
        new Column("Tratamiento", Kind.TEXT, 36));

    private final MovementHistoryService movements;
    private final SaleQueryService sales;
    private final TransferQueryService transfers;
    private final VisibilityService branches;
    private final ScopePolicy policy;
    private final XlsxReportWriter writer;
    private final Clock clock;

    public ReportService(MovementHistoryService movements, SaleQueryService sales, TransferQueryService transfers,
                         VisibilityService branches, ScopePolicy policy, XlsxReportWriter writer, Clock clock) {
        this.movements = movements;
        this.sales = sales;
        this.transfers = transfers;
        this.branches = branches;
        this.policy = policy;
        this.writer = writer;
        this.clock = clock;
    }

    /** One workbook ready to download; {@code rows} is the number of records it holds. */
    public record ReportFile(String name, byte[] content, int rows) {}

    public ReportFile movements(SessionPrincipal principal, String from, String to, UUID branch) {
        return export(principal, from, to, branch, new Category<>("movimientos", "Movimientos", "Reporte de movimientos de inventario",
            "Fecha de registro del movimiento", List.of(), MOVEMENT_COLUMNS,
            (scope, period) -> movements.exportRows(scope, period.start(), period.end(), MAX_ROWS + 1), ReportService::movement));
    }

    public ReportFile sales(SessionPrincipal principal, String from, String to, UUID branch) {
        return export(principal, from, to, branch, new Category<>("ventas", "Ventas", "Reporte de ventas",
            "Fecha de confirmación de la venta", List.of(), SALE_COLUMNS,
            (scope, period) -> sales.exportLines(scope, period.start(), period.end(), MAX_ROWS + 1), ReportService::sale));
    }

    public ReportFile transfers(SessionPrincipal principal, String from, String to, UUID branch, String status) {
        if (status != null && !TransferStatus.isCode(status)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Estado no admitido: use " + TransferStatus.codes() + ".");
        }
        List<Metadata> extra = List.of(new Metadata("Estado", status == null ? "Todos" : capitalized(TransferStatus.labelOf(status))));
        return export(principal, from, to, branch, new Category<>("transferencias", "Transferencias", "Reporte de transferencias",
            "Fecha de solicitud de la transferencia", extra, TRANSFER_COLUMNS,
            (scope, period) -> transfers.exportLines(scope, period.start(), period.end(), status, MAX_ROWS + 1), ReportService::transfer));
    }

    /** What distinguishes one report from another. */
    private record Category<T>(String file, String sheet, String title, String dateFilter, List<Metadata> extra,
                               List<Column> columns, BiFunction<UUID, ReportPeriod, List<T>> reading,
                               Function<T, List<Object>> row) {}

    private <T> ReportFile export(SessionPrincipal principal, String from, String to, UUID branch, Category<T> category) {
        boolean admin = "ADMIN".equals(principal.role());
        if (!admin && !("MANAGER".equals(principal.role()) && principal.branchExternalId() != null)) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "Los reportes exportables corresponden a gerentes y administradores.");
        }
        if (branch != null) policy.requirePublicReference(branch);
        ReportPeriod period = ReportPeriod.parse(from, to);
        UUID requested = branch != null ? branch : admin ? null : principal.branchExternalId();
        if (requested != null) policy.requireHistoryRead(principal, requested);
        Branch scope = requested == null ? null : branches.branch(requested);
        List<T> records = category.reading().apply(requested, period);
        if (records.size() > MAX_ROWS) {
            throw new ApiException(422, "REPORT_TOO_LARGE", "El reporte supera el máximo de 10.000 filas. Reduzca el rango "
                + "de fechas o elija una sucursal; no se generó ningún archivo.");
        }
        List<Metadata> metadata = new ArrayList<>(List.of(
            new Metadata("Sucursal", scope == null ? "Toda la red" : name(scope.code(), scope.name())),
            new Metadata("Período", period.from() + " a " + period.to() + ", ambas fechas incluidas"),
            new Metadata("Fecha del filtro", category.dateFilter()),
            new Metadata("Zona horaria", ComplianceService.BUSINESS_ZONE.getId())));
        metadata.addAll(category.extra());
        metadata.add(new Metadata("Generado", GENERATED.format(local(clock.instant()))));
        metadata.add(new Metadata("Filas", String.valueOf(records.size())));
        if (records.isEmpty()) metadata.add(new Metadata("Nota", "Sin resultados: no hay registros para los filtros indicados."));
        ReportTable table = new ReportTable(category.sheet(), category.title(), metadata, category.columns(),
            records.stream().map(category.row()).toList());
        return new ReportFile("%s_%s_%s.xlsx".formatted(category.file(), period.from(), period.to()), writer.write(table), records.size());
    }

    private static List<Object> movement(MovementExportRow row) {
        BigDecimal signed = "OUT".equals(row.direction()) ? row.quantity().negate() : row.quantity();
        return cells(row.externalId().toString(), local(row.occurredAt()), name(row.branchCode(), row.branchName()), row.sku(),
            row.productName(), MOVEMENT_KINDS.getOrDefault(row.kind(), row.kind()), row.reason(), signed, row.unit(),
            name(row.actorCode(), row.actorName()), row.sourceDocument());
    }

    private static List<Object> sale(SaleExportLine line) {
        return cells(line.saleCode(), local(line.confirmedAt()), name(line.branchCode(), line.branchName()),
            name(line.actorCode(), line.actorName()), line.sku(), line.productName(), line.unit(), line.quantity(), line.unitPrice(),
            line.discountPercent(), line.netAmount().setScale(2, RoundingMode.HALF_UP));
    }

    private static List<Object> transfer(TransferExportLine line) {
        return cells(line.transferCode(), name(line.originCode(), line.originName()), name(line.destinationCode(), line.destinationName()),
            local(line.requestedAt()), local(line.dispatchedAt()), local(line.arrivedAt()), capitalized(TransferStatus.labelOf(line.status())),
            line.sku(), line.unit(), line.requestedQuantity(), line.dispatchedQuantity(), line.receivedQuantity(),
            line.pendingDifference(), treatment(line));
    }

    /** «Resuelto» is never exported: it only documents a decision and would read as a complete reception. */
    private static String treatment(TransferExportLine line) {
        if (line.dispatchedQuantity() == null || line.receivedQuantity() == null
            || line.dispatchedQuantity().compareTo(line.receivedQuantity()) == 0 || line.discrepancyStatus() == null) return null;
        String treatment = line.treatment() == null ? "Sin tratamiento (alerta abierta)" : TREATMENTS.getOrDefault(line.treatment(), line.treatment());
        return line.transitLoss() ? treatment + "; pérdida en tránsito registrada" : treatment;
    }

    private static List<Object> cells(Object... values) {
        return Arrays.asList(values);
    }

    private static String name(String code, String name) {
        return code + " · " + name;
    }

    private static LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ComplianceService.BUSINESS_ZONE);
    }

    private static String capitalized(String text) {
        return text.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + text.substring(1);
    }
}
