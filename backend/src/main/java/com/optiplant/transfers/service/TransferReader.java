package com.optiplant.transfers.service;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.logistics.dto.DeliveryTiming;
import com.optiplant.transfers.dto.TransferResponse;
import com.optiplant.transfers.dto.TransferResponse.Decision;
import com.optiplant.transfers.dto.TransferResponse.Discrepancy;
import com.optiplant.transfers.dto.TransferResponse.Dispatch;
import com.optiplant.transfers.dto.TransferResponse.Event;
import com.optiplant.transfers.dto.TransferResponse.Line;
import com.optiplant.transfers.dto.TransferResponse.Person;
import com.optiplant.transfers.dto.TransferResponse.Prepared;
import com.optiplant.transfers.dto.TransferResponse.Reference;
import com.optiplant.transfers.dto.TransferResponse.SourceDiscrepancy;
import com.optiplant.transfers.dto.TransferResponse.TransitLoss;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds transfer responses from their stored lines and events; authorization is checked by the callers. The origin's
 * availability is its current physical stock (no reservations), read only when {@code availability} is true.
 */
@Component
public class TransferReader {
    private static final String TRANSFER = """
        SELECT t.id,t.external_id,t.code,t.status,t.requested_at,CURRENT_TIMESTAMP AS read_at,
            o.external_id AS origin,o.code AS origin_code,o.name AS origin_name,o.active AS origin_active,
            d.external_id AS destination,d.code AS destination_code,d.name AS destination_name,d.active AS destination_active,
            r.code AS requester_code,r.full_name AS requester_name,
            ae.occurred_at AS approved_at,au.code AS approver_code,au.full_name AS approver_name,
            ld.carrier_text,ld.dispatched_at,ld.estimated_arrival_at,ld.route_priority_snapshot,lr.code AS route_code,
            du.code AS dispatcher_code,du.full_name AS dispatcher_name,
            la.arrived_at,cu.code AS receiver_code,cu.full_name AS receiver_name,
            x.external_id AS discrepancy,x.status AS discrepancy_status,x.opened_at,x.treatment,x.resolved_at,
            xu.code AS resolver_code,xu.full_name AS resolver_name,
            lo.occurred_at AS loss_at,lo.reason AS loss_reason,lo.evidence AS loss_evidence,
            lu.code AS loss_code,lu.full_name AS loss_name,
            sx.external_id AS source_discrepancy,st.external_id AS source_transfer,st.code AS source_code
        FROM transfers_transfer t JOIN identity_branch o ON o.id=t.origin_branch_id
        JOIN identity_branch d ON d.id=t.destination_branch_id JOIN identity_user_account r ON r.id=t.requested_by
        LEFT JOIN transfers_event ae ON ae.transfer_id=t.id AND ae.to_status='APPROVED'
        LEFT JOIN identity_user_account au ON au.id=ae.actor_id
        LEFT JOIN logistics_dispatch ld ON ld.transfer_id=t.id LEFT JOIN logistics_route lr ON lr.id=ld.route_id
        LEFT JOIN identity_user_account du ON du.id=ld.dispatched_by
        LEFT JOIN logistics_arrival la ON la.dispatch_id=ld.id LEFT JOIN identity_user_account cu ON cu.id=la.confirmed_by
        LEFT JOIN logistics_discrepancy x ON x.arrival_id=la.id LEFT JOIN identity_user_account xu ON xu.id=x.resolved_by
        LEFT JOIN inventory_operation lo ON lo.discrepancy_id=x.id AND lo.kind='TRANSIT_LOSS'
        LEFT JOIN identity_user_account lu ON lu.id=lo.authorized_by
        LEFT JOIN logistics_discrepancy sx ON sx.id=t.source_discrepancy_id LEFT JOIN logistics_arrival sa ON sa.id=sx.arrival_id
        LEFT JOIN logistics_dispatch sd ON sd.id=sa.dispatch_id LEFT JOIN transfers_transfer st ON st.id=sd.transfer_id
        """;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public TransferReader(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** The full transfer with its lines and state history. */
    public TransferResponse detail(long transferId, boolean availability) {
        TransferResponse transfer = transfers(TRANSFER + "WHERE t.id=?", List.of(transferId), availability).getFirst();
        List<Event> events = jdbc.query("""
            SELECT e.from_status,e.to_status,e.occurred_at,u.code,u.full_name,e.reason,e.preparation_snapshot::text
            FROM transfers_event e JOIN identity_user_account u ON u.id=e.actor_id WHERE e.transfer_id=? ORDER BY e.sequence_no""",
            (r, i) -> new Event(r.getString(1), r.getString(2), r.getObject(3, OffsetDateTime.class).toInstant(),
                new Person(r.getString(4), r.getString(5)), r.getString(6), preparation(r.getString(7))), transferId);
        Discrepancy discrepancy = transfer.discrepancy();
        if (discrepancy != null) {
            List<Reference> resends = jdbc.query("""
                SELECT t.external_id,t.code,t.status FROM transfers_transfer t JOIN logistics_discrepancy x ON x.id=t.source_discrepancy_id
                WHERE x.external_id=? ORDER BY t.id""", (r, i) -> new Reference(r.getObject(1, UUID.class), r.getString(2),
                r.getString(3)), discrepancy.externalId());
            discrepancy = new Discrepancy(discrepancy.externalId(), discrepancy.status(), discrepancy.openedAt(),
                discrepancy.treatment(), discrepancy.resolvedAt(), discrepancy.resolvedBy(), discrepancy.transitLoss(), resends);
        }
        return new TransferResponse(transfer.externalId(), transfer.code(), transfer.status(), transfer.origin(),
            transfer.destination(), transfer.requestedAt(), transfer.requestedBy(), transfer.approval(), transfer.dispatch(),
            transfer.arrival(), transfer.timing(), discrepancy, transfer.sourceDiscrepancy(), transfer.lines(), events);
    }

    /** Transfers selected by {@code where} (appended to the base query), each with its lines and without events. */
    public List<TransferResponse> list(String where, List<Object> arguments) {
        return transfers(TRANSFER + where, arguments, false);
    }

    private record Row(long id, UUID externalId, String code, String status, OffsetDateTime requestedAt, Branch origin,
                       Branch destination, Person requester, Decision approval, Dispatch dispatch, Decision arrival,
                       Discrepancy discrepancy, SourceDiscrepancy source, java.time.Instant readAt) {}

    private List<TransferResponse> transfers(String sql, List<Object> arguments, boolean availability) {
        List<Row> rows = jdbc.query(sql, (r, i) -> new Row(r.getLong("id"), r.getObject("external_id", UUID.class),
            r.getString("code"), r.getString("status"), r.getObject("requested_at", OffsetDateTime.class),
            new Branch(r.getObject("origin", UUID.class), r.getString("origin_code"), r.getString("origin_name"),
                r.getBoolean("origin_active")),
            new Branch(r.getObject("destination", UUID.class), r.getString("destination_code"), r.getString("destination_name"),
                r.getBoolean("destination_active")),
            new Person(r.getString("requester_code"), r.getString("requester_name")),
            r.getString("approver_code") == null ? null : new Decision(r.getObject("approved_at", OffsetDateTime.class).toInstant(),
                new Person(r.getString("approver_code"), r.getString("approver_name"))),
            r.getString("carrier_text") == null ? null : new Dispatch(r.getString("carrier_text"),
                r.getObject("dispatched_at", OffsetDateTime.class).toInstant(),
                r.getObject("estimated_arrival_at", OffsetDateTime.class).toInstant(),
                new Person(r.getString("dispatcher_code"), r.getString("dispatcher_name")), r.getString("route_code"),
                r.getString("route_priority_snapshot")),
            r.getString("receiver_code") == null ? null : new Decision(r.getObject("arrived_at", OffsetDateTime.class).toInstant(),
                new Person(r.getString("receiver_code"), r.getString("receiver_name"))),
            r.getObject("discrepancy") == null ? null : new Discrepancy(r.getObject("discrepancy", UUID.class),
                r.getString("discrepancy_status"), r.getObject("opened_at", OffsetDateTime.class).toInstant(), r.getString("treatment"),
                instant(r.getObject("resolved_at", OffsetDateTime.class)), r.getString("resolver_code") == null ? null
                    : new Person(r.getString("resolver_code"), r.getString("resolver_name")),
                r.getString("loss_code") == null ? null : new TransitLoss(r.getObject("loss_at", OffsetDateTime.class).toInstant(),
                    new Person(r.getString("loss_code"), r.getString("loss_name")), r.getString("loss_reason"),
                    r.getString("loss_evidence")), null),
            r.getObject("source_discrepancy") == null ? null : new SourceDiscrepancy(r.getObject("source_discrepancy", UUID.class),
                r.getObject("source_transfer", UUID.class), r.getString("source_code")),
            r.getObject("read_at", OffsetDateTime.class).toInstant()), arguments.toArray());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Line>> lines = new LinkedHashMap<>();
        rows.forEach(row -> lines.put(row.id(), new ArrayList<>()));
        String ids = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("""
            SELECT l.transfer_id,l.line_no,p.external_id,l.sku_snapshot,l.product_name_snapshot,l.unit_name_snapshot,
                l.factor_snapshot,p.base_unit_name,l.requested_commercial_qty,l.requested_base_qty,l.prepared_commercial_qty,
                l.prepared_base_qty,dl.dispatched_commercial_qty,dl.dispatched_base_qty,u.divisible,p.base_divisible,%s AS available,
                al.received_commercial_qty,al.received_base_qty,it.pending_base_qty
            FROM transfers_line l JOIN catalog_product p ON p.id=l.product_id JOIN transfers_transfer t ON t.id=l.transfer_id
            JOIN catalog_product_unit u ON u.id=l.product_unit_id LEFT JOIN logistics_dispatch_line dl ON dl.transfer_line_id=l.id
            LEFT JOIN logistics_arrival_line al ON al.dispatch_line_id=dl.id LEFT JOIN inventory_transit it ON it.dispatch_line_id=dl.id
            WHERE l.transfer_id IN (%s) ORDER BY l.transfer_id,l.line_no""".formatted(availability
                ? "(SELECT coalesce(sum(ib.physical_qty),0) FROM inventory_balance ib WHERE ib.branch_id=t.origin_branch_id "
                    + "AND ib.product_id=l.product_id)" : "NULL::numeric", ids),
            (java.sql.ResultSet r) -> {
                lines.get(r.getLong("transfer_id")).add(new Line(r.getInt("line_no"), r.getObject("external_id", UUID.class),
                    r.getString("sku_snapshot"), r.getString("product_name_snapshot"), r.getString("unit_name_snapshot"),
                    plain(r.getBigDecimal("factor_snapshot")), r.getString("base_unit_name"),
                    quantity(r.getBigDecimal("requested_commercial_qty")), quantity(r.getBigDecimal("requested_base_qty")),
                    quantity(r.getBigDecimal("prepared_commercial_qty")), quantity(r.getBigDecimal("prepared_base_qty")),
                    quantity(r.getBigDecimal("dispatched_commercial_qty")), quantity(r.getBigDecimal("dispatched_base_qty")),
                    quantity(r.getBigDecimal("available")), r.getBoolean("divisible"), r.getBoolean("base_divisible"),
                    quantity(r.getBigDecimal("received_commercial_qty")), quantity(r.getBigDecimal("received_base_qty")),
                    difference(r.getBigDecimal("dispatched_commercial_qty"), r.getBigDecimal("received_commercial_qty")),
                    difference(r.getBigDecimal("dispatched_base_qty"), r.getBigDecimal("received_base_qty")),
                    r.getBigDecimal("dispatched_base_qty") == null ? null : quantity(r.getBigDecimal("pending_base_qty") == null
                        ? BigDecimal.ZERO : r.getBigDecimal("pending_base_qty"))));
            }, rows.stream().map(Row::id).toArray());
        return rows.stream().map(row -> new TransferResponse(row.externalId(), row.code(), row.status(), row.origin(),
            row.destination(), row.requestedAt().toInstant(), row.requester(), row.approval(), row.dispatch(), row.arrival(),
            timing(row), row.discrepancy(), row.source(), List.copyOf(lines.get(row.id())),
            null)).toList();
    }

    private List<Prepared> preparation(String snapshot) {
        if (snapshot == null) return null;
        List<Prepared> prepared = new ArrayList<>();
        for (JsonNode line : json.readTree(snapshot)) {
            prepared.add(new Prepared(line.get("lineNo").asInt(), line.get("quantity").asText(), line.get("baseQuantity").asText()));
        }
        return List.copyOf(prepared);
    }

    /** RF-0040: the arrival is complete only when the transfer was received complete. */
    private static DeliveryTiming timing(Row row) {
        return row.dispatch() == null ? null : DeliveryTiming.of(row.dispatch().departedAt(), row.dispatch().estimatedArrivalAt(),
            row.arrival() == null ? null : row.arrival().occurredAt(),
            TransferStatus.RECEIVED_COMPLETE.name().equals(row.status()), row.readAt());
    }

    /** Dispatched minus received, once the arrival exists. */
    private static String difference(BigDecimal dispatched, BigDecimal received) {
        return received == null ? null : quantity(dispatched.subtract(received));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(3).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
