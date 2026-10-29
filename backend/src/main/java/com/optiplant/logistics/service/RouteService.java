package com.optiplant.logistics.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.logistics.dto.RouteRequest;
import com.optiplant.logistics.dto.RouteResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directed routes classified by manual priority (RF-0041, INC-017/D01–D02, INC-003/D06). Only the administrator
 * maintains them; managers and operators consult the active routes of their branch and use them when dispatching.
 * A trajectory exists once: an inactive route is reactivated instead of registered again.
 */
@Service
public class RouteService {
    private static final String SELECT = """
        SELECT r.external_id,r.code,r.priority,r.active,
            o.external_id AS origin,o.code AS origin_code,o.name AS origin_name,o.active AS origin_active,
            d.external_id AS destination,d.code AS destination_code,d.name AS destination_name,d.active AS destination_active
        FROM logistics_route r JOIN identity_branch o ON o.id=r.origin_branch_id JOIN identity_branch d ON d.id=r.destination_branch_id
        """;
    private static final String ORDER = " ORDER BY CASE r.priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,r.code";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RouteService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The route a dispatch uses: its internal id, code and current priority. */
    public record ActiveRoute(long id, String code, String priority) {}

    /**
     * RF-0041-CA-01: routes ordered by their classification, optionally only one priority. The administrator reads every
     * route, active or not; local profiles read the active routes whose origin or destination is their branch.
     */
    @Transactional(readOnly = true)
    public List<RouteResponse> list(SessionPrincipal principal, String priority) {
        if (priority != null && !RoutePriority.isCode(priority)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Prioridad no admitida: use " + RoutePriority.codes() + ".");
        }
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        if (!"ADMIN".equals(principal.role())) {
            where.append(" AND r.active AND ? IN (o.external_id,d.external_id)");
            arguments.add(principal.branchExternalId());
        }
        if (priority != null) {
            where.append(" AND r.priority=?");
            arguments.add(priority);
        }
        return jdbc.query(SELECT + where + ORDER, (r, i) -> response(r), arguments.toArray());
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {
        long origin = branch(request.origin(), "origen");
        long destination = branch(request.destination(), "destino");
        if (origin == destination) {
            throw new ApiException(400, "SAME_BRANCH", "El origen y el destino de una ruta deben ser sucursales distintas. "
                + "No se registró la ruta.");
        }
        List<String> existing = jdbc.queryForList("SELECT code FROM logistics_route WHERE origin_branch_id=? "
            + "AND destination_branch_id=?", String.class, origin, destination);
        if (!existing.isEmpty()) throw routeExists(existing.getFirst());
        requireFreeCode(request.code(), null);
        Timestamp now = Timestamp.from(clock.instant());
        UUID externalId = UUID.randomUUID();
        // A concurrent registration of the same trajectory or code may commit between the checks above and this insert.
        // ON CONFLICT waits for it and then inserts nothing, without raising an error, so the transaction stays usable and
        // each business conflict is checked by name; the unique constraints remain the guarantee.
        boolean inserted = !jdbc.queryForList("""
            INSERT INTO logistics_route(external_id,code,origin_branch_id,destination_branch_id,priority,active,created_at,updated_at)
            VALUES (?,?,?,?,?,TRUE,?,?) ON CONFLICT DO NOTHING RETURNING id""", Long.class, externalId, request.code(), origin,
            destination, request.priority(), now, now).isEmpty();
        if (!inserted) {
            List<String> raced = jdbc.queryForList("SELECT code FROM logistics_route WHERE origin_branch_id=? "
                + "AND destination_branch_id=?", String.class, origin, destination);
            if (!raced.isEmpty()) throw routeExists(raced.getFirst());
            requireFreeCode(request.code(), null);
            // Neither conflict is visible any more (the winner changed its code meanwhile): nothing was registered and
            // the same request can simply be repeated, instead of naming a conflict that no longer exists.
            throw new ApiException(409, "ROUTE_CONFLICT", "Otra ruta se registró o modificó al mismo tiempo. No se registró "
                + "la ruta: repita la operación.");
        }
        return find(externalId);
    }

    /** Changes the code, priority or active state; dispatches already registered keep their priority snapshot. */
    @Transactional
    public RouteResponse update(UUID externalId, RouteRequest request) {
        Long id = jdbc.query("SELECT id FROM logistics_route WHERE external_id=? FOR UPDATE", (r, i) -> r.getLong(1), externalId)
            .stream().findFirst().orElseThrow(RouteService::notFound);
        requireFreeCode(request.code(), id);
        try {
            jdbc.update("UPDATE logistics_route SET code=?,priority=?,active=?,updated_at=? WHERE id=?", request.code(),
                request.priority(), request.active(), Timestamp.from(clock.instant()), id);
        } catch (DuplicateKeyException error) {
            throw codeExists();
        }
        return find(externalId);
    }

    /**
     * Contract for the dispatch (RF-0035): the active route of a directed pair, share-locked so it cannot be deactivated
     * or reclassified until the dispatch commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ActiveRoute> activeRoute(long originId, long destinationId) {
        return jdbc.query("SELECT id,code,priority FROM logistics_route WHERE origin_branch_id=? AND destination_branch_id=? "
                + "AND active FOR SHARE", (r, i) -> new ActiveRoute(r.getLong(1), r.getString(2), r.getString(3)),
            originId, destinationId).stream().findFirst();
    }

    private RouteResponse find(UUID externalId) {
        return jdbc.query(SELECT + " WHERE r.external_id=?", (r, i) -> response(r), externalId).getFirst();
    }

    private long branch(UUID externalId, String role) {
        Map<String, Object> branch = jdbc.queryForList("SELECT id,active FROM identity_branch WHERE external_id=?", externalId)
            .stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal " + role
                + ". No se registró la ruta."));
        if (!(Boolean) branch.get("active")) {
            throw new ApiException(409, "BRANCH_INACTIVE", "La sucursal " + role + " está inactiva y no admite nuevas rutas. "
                + "No se registró la ruta.");
        }
        return ((Number) branch.get("id")).longValue();
    }

    private void requireFreeCode(String code, Long exceptId) {
        Integer taken = jdbc.queryForObject("SELECT count(*) FROM logistics_route WHERE code=? AND id IS DISTINCT FROM ?",
            Integer.class, code, exceptId);
        if (taken != null && taken > 0) throw codeExists();
    }

    private static RouteResponse response(java.sql.ResultSet r) throws java.sql.SQLException {
        return new RouteResponse(r.getObject("external_id", UUID.class), r.getString("code"),
            new Branch(r.getObject("origin", UUID.class), r.getString("origin_code"), r.getString("origin_name"),
                r.getBoolean("origin_active")),
            new Branch(r.getObject("destination", UUID.class), r.getString("destination_code"), r.getString("destination_name"),
                r.getBoolean("destination_active")),
            r.getString("priority"), r.getBoolean("active"));
    }

    private static ApiException routeExists(String code) {
        return new ApiException(409, "ROUTE_EXISTS", "Ya existe la ruta " + code + " con ese origen y destino: cambie su "
            + "prioridad o reactívela en lugar de registrarla de nuevo.");
    }

    private static ApiException codeExists() {
        return new ApiException(409, "CODE_EXISTS", "Ya existe una ruta con ese código.");
    }

    private static ApiException notFound() {
        return new ApiException(404, "ROUTE_NOT_FOUND", "No se encontró la ruta.");
    }
}
