package com.optiplant.transfers.service;

import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.transfers.dto.TransferResponse;
import com.optiplant.transfers.dto.TransferResponse.Decision;
import com.optiplant.transfers.dto.TransferResponse.Event;
import com.optiplant.transfers.dto.TransferResponse.Line;
import com.optiplant.transfers.dto.TransferResponse.Person;
import com.optiplant.transfers.dto.TransferResponse.Prepared;
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
        SELECT t.id,t.external_id,t.code,t.status,t.requested_at,
            o.external_id AS origin,o.code AS origin_code,o.name AS origin_name,o.active AS origin_active,
            d.external_id AS destination,d.code AS destination_code,d.name AS destination_name,d.active AS destination_active,
            r.code AS requester_code,r.full_name AS requester_name,
            ae.occurred_at AS approved_at,au.code AS approver_code,au.full_name AS approver_name
        FROM transfers_transfer t JOIN identity_branch o ON o.id=t.origin_branch_id
        JOIN identity_branch d ON d.id=t.destination_branch_id JOIN identity_user_account r ON r.id=t.requested_by
        LEFT JOIN transfers_event ae ON ae.transfer_id=t.id AND ae.to_status='APPROVED'
        LEFT JOIN identity_user_account au ON au.id=ae.actor_id
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
        return new TransferResponse(transfer.externalId(), transfer.code(), transfer.status(), transfer.origin(),
            transfer.destination(), transfer.requestedAt(), transfer.requestedBy(), transfer.approval(), transfer.lines(), events);
    }

    /** Transfers selected by {@code where} (appended to the base query), each with its lines and without events. */
    public List<TransferResponse> list(String where, List<Object> arguments) {
        return transfers(TRANSFER + where, arguments, false);
    }

    private record Row(long id, UUID externalId, String code, String status, OffsetDateTime requestedAt, Branch origin,
                       Branch destination, Person requester, Decision approval) {}

    private List<TransferResponse> transfers(String sql, List<Object> arguments, boolean availability) {
        List<Row> rows = jdbc.query(sql, (r, i) -> new Row(r.getLong("id"), r.getObject("external_id", UUID.class),
            r.getString("code"), r.getString("status"), r.getObject("requested_at", OffsetDateTime.class),
            new Branch(r.getObject("origin", UUID.class), r.getString("origin_code"), r.getString("origin_name"),
                r.getBoolean("origin_active")),
            new Branch(r.getObject("destination", UUID.class), r.getString("destination_code"), r.getString("destination_name"),
                r.getBoolean("destination_active")),
            new Person(r.getString("requester_code"), r.getString("requester_name")),
            r.getString("approver_code") == null ? null : new Decision(r.getObject("approved_at", OffsetDateTime.class).toInstant(),
                new Person(r.getString("approver_code"), r.getString("approver_name")))), arguments.toArray());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Line>> lines = new LinkedHashMap<>();
        rows.forEach(row -> lines.put(row.id(), new ArrayList<>()));
        String ids = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("""
            SELECT l.transfer_id,l.line_no,p.external_id,l.sku_snapshot,l.product_name_snapshot,l.unit_name_snapshot,
                l.factor_snapshot,p.base_unit_name,l.requested_commercial_qty,l.requested_base_qty,l.prepared_commercial_qty,
                l.prepared_base_qty,u.divisible,p.base_divisible,%s AS available
            FROM transfers_line l JOIN catalog_product p ON p.id=l.product_id JOIN transfers_transfer t ON t.id=l.transfer_id
            JOIN catalog_product_unit u ON u.id=l.product_unit_id
            WHERE l.transfer_id IN (%s) ORDER BY l.transfer_id,l.line_no""".formatted(availability
                ? "(SELECT coalesce(sum(ib.physical_qty),0) FROM inventory_balance ib WHERE ib.branch_id=t.origin_branch_id "
                    + "AND ib.product_id=l.product_id)" : "NULL::numeric", ids),
            (java.sql.ResultSet r) -> {
                lines.get(r.getLong("transfer_id")).add(new Line(r.getInt("line_no"), r.getObject("external_id", UUID.class),
                    r.getString("sku_snapshot"), r.getString("product_name_snapshot"), r.getString("unit_name_snapshot"),
                    plain(r.getBigDecimal("factor_snapshot")), r.getString("base_unit_name"),
                    quantity(r.getBigDecimal("requested_commercial_qty")), quantity(r.getBigDecimal("requested_base_qty")),
                    quantity(r.getBigDecimal("prepared_commercial_qty")), quantity(r.getBigDecimal("prepared_base_qty")),
                    quantity(r.getBigDecimal("available")), r.getBoolean("divisible"), r.getBoolean("base_divisible")));
            }, rows.stream().map(Row::id).toArray());
        return rows.stream().map(row -> new TransferResponse(row.externalId(), row.code(), row.status(), row.origin(),
            row.destination(), row.requestedAt().toInstant(), row.requester(), row.approval(), List.copyOf(lines.get(row.id())),
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

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(3).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
