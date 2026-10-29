package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-0041: the administrator maintains directed routes classified by manual priority; local profiles consult those of
 * their branch. RF-0035: the origin operator dispatches an approved transfer through its active route. The dispatch is
 * indivisible: every line leaves the origin into transit at the origin's frozen average, or nothing changes; it is applied
 * once, whether repeated with the same key, with another key or concurrently, and the destination receives nothing.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=dispatch-initial", "app.admin-password=private initial password"})
class RouteAndDispatchHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "optiplant_test_runtime");
        r.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    /** a: origin, b: destination, c: an unrelated branch. */
    UUID a, b, c, product, other;
    String suffix;
    Browser admin, originOperator, originManager, destinationOperator, destinationManager, otherOperator;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        c = branch("c");
        product = product("Maceta", a, b, c);
        other = product("Sustrato", a, b);
        admin = account("ADMIN", null);
        originOperator = account("OPERATOR", a);
        originManager = account("MANAGER", a);
        destinationOperator = account("OPERATOR", b);
        destinationManager = account("MANAGER", b);
        otherOperator = account("OPERATOR", c);
        stock(product, "30");
        stock(other, "10");
    }

    // ---------------------------------------------------------------- RF-0041 routes

    @Test void administratorClassifiesDirectedRoutesByManualPriority() throws Exception {
        JsonNode ab = read(created(route("AB", a, b, "LOW")));
        assertThat(ab.get("priority").asText()).isEqualTo("LOW");
        assertThat(ab.get("active").asBoolean()).isTrue();
        assertThat(ab.at("/origin/externalId").asText()).isEqualTo(a.toString());
        assertThat(ab.at("/destination/externalId").asText()).isEqualTo(b.toString());
        // Directed: B→A is another route, with its own classification.
        String ba = read(created(route("BA", b, a, "HIGH"))).get("externalId").asText();
        String ac = read(created(route("AC", a, c, "MEDIUM"))).get("externalId").asText();

        // RF-0041-CA-01: the classification is one of the approved values and orders the listing.
        assertThat(codes(admin.get("/logistics/routes"))).containsSubsequence("BA" + suffix, "AC" + suffix, "AB" + suffix);
        assertThat(codes(admin.get("/logistics/routes?priority=HIGH"))).contains("BA" + suffix).doesNotContain("AB" + suffix, "AC" + suffix);

        // Reclassifying and renaming keep the trajectory and the identifier.
        HttpResponse<String> changed = admin.post("/logistics/routes/" + ab.get("externalId").asText() + "/update",
            Map.of("code", "AB2" + suffix, "priority", "HIGH", "active", true));
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
        assertThat(read(changed).get("priority").asText()).isEqualTo("HIGH");
        assertThat(read(changed).at("/origin/externalId").asText()).isEqualTo(a.toString());
        assertThat(codes(admin.get("/logistics/routes?priority=HIGH"))).contains("AB2" + suffix, "BA" + suffix);

        // Local profiles read only the active routes of their branch; an inactive one stays visible to the administrator.
        assertThat(admin.post("/logistics/routes/" + ac + "/update", Map.of("code", "AC" + suffix, "priority", "MEDIUM",
            "active", false)).statusCode()).isEqualTo(200);
        assertThat(codes(originManager.get("/logistics/routes"))).contains("AB2" + suffix, "BA" + suffix).doesNotContain("AC" + suffix);
        assertThat(codes(otherOperator.get("/logistics/routes"))).doesNotContain("AB2" + suffix, "BA" + suffix, "AC" + suffix);
        assertThat(codes(admin.get("/logistics/routes"))).contains("AC" + suffix);
        assertThat(ba).isNotBlank();
    }

    @Test void invalidRoutesAndNonAdministratorsAreRejectedWithoutChanges() throws Exception {
        String ab = read(created(route("AB", a, b, "MEDIUM"))).get("externalId").asText();
        int routes = routes();

        assertError(route("AB-bis", a, b, "HIGH"), 409, "ROUTE_EXISTS");
        assertError(route("AB", b, a, "HIGH"), 409, "CODE_EXISTS");
        assertError(route("AA", a, a, "HIGH"), 400, "SAME_BRANCH");
        assertError(route("AX", a, b, "URGENT"), 400, "VALIDATION_FAILED");
        assertError(admin.post("/logistics/routes", Map.of("code", "AX" + suffix, "origin", a.toString())), 400, "VALIDATION_FAILED");
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", c);
        assertError(route("AC", a, c, "HIGH"), 409, "BRANCH_INACTIVE");
        HttpResponse<String> moved = admin.post("/logistics/routes/" + ab + "/update", Map.of("code", "AB" + suffix,
            "priority", "HIGH", "active", true, "destination", c.toString()));
        assertError(moved, 400, "VALIDATION_FAILED");
        assertThat(read(moved).get("detail").asText()).contains("no se modifican");
        for (Browser local : List.of(originManager, originOperator)) {
            assertError(local.post("/logistics/routes", Map.of("code", "X" + suffix, "origin", b.toString(),
                "destination", a.toString(), "priority", "HIGH")), 403, "ROLE_FORBIDDEN");
            assertError(local.post("/logistics/routes/" + ab + "/update", Map.of("code", "AB" + suffix, "priority", "HIGH",
                "active", true)), 403, "ROLE_FORBIDDEN");
        }
        assertThat(routes()).isEqualTo(routes);
        assertThat(jdbc.queryForObject("SELECT priority FROM logistics_route WHERE external_id=?::uuid", String.class, ab))
            .isEqualTo("MEDIUM");
        assertThat(admin.get("/logistics/routes?priority=URGENT").statusCode()).isEqualTo(400);
    }

    /**
     * The collision path, made deterministic: another transaction inserts the conflicting route and keeps it uncommitted,
     * the registration blocks on the unique index, and only then does the other transaction finish. Without that, two
     * "simultaneous" requests are usually served one after the other and never reach this path.
     */
    @Test void aRegistrationBlockedByAnUncommittedConflictReportsWhichOneWon() throws Exception {
        int before = routes();
        // Same trajectory, different code: the waiting registration inserts nothing and reports the trajectory.
        HttpResponse<String> trajectory = registerWhileHeld("HELD1", a, b, () -> route("WAIT1", a, b, "LOW"), true);
        assertError(trajectory, 409, "ROUTE_EXISTS");
        assertThat(read(trajectory).get("detail").asText()).contains("HELD1" + suffix);
        // Same code, other trajectory: it reports the code.
        assertError(registerWhileHeld("HELD2", b, a, () -> route("HELD2", a, c, "LOW"), true), 409, "CODE_EXISTS");
        // If the other transaction rolls back, the waiting registration is created.
        assertThat(registerWhileHeld("HELD3", c, a, () -> route("WAIT3", c, a, "MEDIUM"), false).statusCode()).isEqualTo(201);
        assertThat(routes()).isEqualTo(before + 3);
        assertThat(jdbc.queryForObject("SELECT code FROM logistics_route WHERE origin_branch_id=(SELECT id FROM identity_branch "
            + "WHERE external_id=?) AND destination_branch_id=(SELECT id FROM identity_branch WHERE external_id=?)", String.class, c, a))
            .isEqualTo("WAIT3" + suffix);
    }

    /**
     * Inserts route {@code code} from {@code origin} to {@code destination} in a transaction of its own, starts
     * {@code registration}, waits until it is blocked on that row's lock, then commits or rolls back and returns its response.
     */
    HttpResponse<String> registerWhileHeld(String code, UUID origin, UUID destination, Callable<HttpResponse<String>> registration,
                                           boolean commit) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection held = dataSource.getConnection()) {
            held.setAutoCommit(false);
            try (PreparedStatement insert = held.prepareStatement("INSERT INTO logistics_route(external_id,code,origin_branch_id,"
                + "destination_branch_id,priority) SELECT ?,?,o.id,d.id,'HIGH' FROM identity_branch o, identity_branch d "
                + "WHERE o.external_id=? AND d.external_id=?")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, code + suffix);
                insert.setObject(3, origin);
                insert.setObject(4, destination);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            Future<HttpResponse<String>> response = pool.submit(registration);
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' "
                + "AND query LIKE '%INSERT INTO logistics_route%'", Integer.class) == 0) {
                assertThat(response.isDone()).as("the registration finished without waiting for the held row").isFalse();
                assertThat(System.nanoTime()).as("the registration never blocked on the held row").isLessThan(deadline);
                Thread.sleep(20);
            }
            if (commit) held.commit(); else held.rollback();
            return response.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- RF-0035 dispatch

    @Test void originOperatorDispatchesIntoTransitAtTheFrozenOriginCost() throws Exception {
        created(route("AB", a, b, "HIGH"));
        String id = approved();
        // Before the dispatch the contract sends its fields as null, never omits them.
        JsonNode pending = read(originOperator.get("/transfers/" + id));
        assertThat(pending.has("dispatch") && pending.get("dispatch").isNull()).isTrue();
        assertThat(pending.at("/lines/0").has("dispatchedQuantity") && pending.at("/lines/0/dispatchedQuantity").isNull()).isTrue();
        assertThat(pending.at("/lines/0/dispatchedBaseQuantity").isNull()).isTrue();
        // Minute precision, as the form captures it: the approval's own minute is accepted.
        OffsetDateTime departed = OffsetDateTime.now(ZoneOffset.ofHours(-5)).truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime estimated = departed.plusHours(26);

        HttpResponse<String> done = originOperator.post("/transfers/" + id + "/dispatch", dispatch("Transportes Andinos", departed, estimated));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        JsonNode transfer = read(done);
        // RF-0035-CA-01: quantity sent, estimated arrival and carrier are consultable.
        assertThat(transfer.get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(transfer.at("/dispatch/carrier").asText()).isEqualTo("Transportes Andinos");
        assertThat(OffsetDateTime.parse(transfer.at("/dispatch/estimatedArrivalAt").asText()).toInstant()).isEqualTo(estimated.toInstant());
        assertThat(OffsetDateTime.parse(transfer.at("/dispatch/departedAt").asText()).toInstant()).isEqualTo(departed.toInstant());
        assertThat(transfer.at("/dispatch/routeCode").asText()).isEqualTo("AB" + suffix);
        assertThat(transfer.at("/dispatch/routePriority").asText()).isEqualTo("HIGH");
        assertThat(transfer.at("/lines/0/dispatchedQuantity").asText()).isEqualTo("1.000");
        assertThat(transfer.at("/lines/0/dispatchedBaseQuantity").asText()).isEqualTo("12.000");
        assertThat(transfer.at("/lines/1/dispatchedQuantity").asText()).isEqualTo("5.000");
        assertThat(transfer.at("/events/3/toStatus").asText()).isEqualTo("IN_TRANSIT");
        JsonNode seen = read(destinationOperator.get("/transfers/" + id));
        assertThat(seen.at("/dispatch/carrier").asText()).isEqualTo("Transportes Andinos");
        assertThat(seen.at("/lines/0/dispatchedQuantity").asText()).isEqualTo("1.000");

        // The origin leaves 12 and 5 units at cost 5; the transit holds them with that value; the destination has nothing.
        assertThat(balances()).containsExactly("a" + suffix + ":Maceta:18.000", "a" + suffix + ":Sustrato:5.000");
        assertThat(transit(id)).containsExactly("12.000|60.000000000000000000|0.000000000000000000", "5.000|25.000000000000000000|0.000000000000000000");
        assertThat(jdbc.queryForList("SELECT m.direction||':'||m.base_qty||':'||m.historical_base_cost FROM inventory_movement m "
            + "JOIN logistics_dispatch_line dl ON dl.id=m.dispatch_line_id JOIN logistics_dispatch d ON d.id=dl.dispatch_id "
            + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid ORDER BY m.id", String.class, id))
            .containsExactly("OUT:12.000:5.000000", "OUT:5.000:5.000000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operation o JOIN logistics_dispatch d ON d.id=o.dispatch_id "
            + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid AND o.kind='DISPATCH'", Integer.class, id))
            .isEqualTo(1);

        // Reclassifying the route later does not change the priority recorded at dispatch.
        String routeId = jdbc.queryForObject("SELECT external_id::text FROM logistics_route WHERE code=?", String.class, "AB" + suffix);
        assertThat(admin.post("/logistics/routes/" + routeId + "/update", Map.of("code", "AB" + suffix, "priority", "LOW",
            "active", true)).statusCode()).isEqualTo(200);
        assertThat(read(originManager.get("/transfers/" + id)).at("/dispatch/routePriority").asText()).isEqualTo("HIGH");

        // A transfer in transit is neither cancelled nor dispatched again.
        assertError(originManager.post("/transfers/" + id + "/cancel", Map.of("reason", "Error")), 409, "TRANSFER_IN_TRANSIT");
    }

    @Test void aLineWithoutStockRejectsTheWholeDispatch() throws Exception {
        created(route("AB", a, b, "MEDIUM"));
        String id = approved();
        // Another exit leaves 2 units of the second product, which needs 5.
        HttpResponse<String> exit = originOperator.post("/inventory/movements", Map.of("kind", "MANUAL_EXIT", "product",
            other.toString(), "unit", "unidad", "quantity", "8", "reason", "Merma"));
        assertThat(exit.statusCode()).as(exit.body()).isEqualTo(201);
        Snapshot before = snapshot(id);

        HttpResponse<String> rejected = originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("Transportes Andinos"));
        assertError(rejected, 409, "INSUFFICIENT_STOCK");
        assertThat(read(rejected).get("detail").asText()).startsWith("Línea 2: existencias insuficientes en el origen: se requieren 5 y hay 2 (unidad).")
            .contains("no hubo salida ni tránsito");
        // RF-0035-CA-02: no exit, no transit, no dispatch row, the first line untouched and the transfer still approved.
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(read(originOperator.get("/transfers/" + id)).get("dispatch").isNull()).isTrue();
    }

    @Test void repeatedAndConcurrentDispatchesMoveStockOnce() throws Exception {
        created(route("AB", a, b, "LOW"));
        String id = approved();
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = dispatchNow("Transportes Andinos");

        HttpResponse<String> first = originOperator.post("/transfers/" + id + "/dispatch", body, key);
        HttpResponse<String> replay = originOperator.post("/transfers/" + id + "/dispatch", body, key);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(read(replay)).isEqualTo(read(first));
        assertError(originOperator.post("/transfers/" + id + "/dispatch", body), 409, "TRANSFER_STATUS_CONFLICT");
        assertThat(movements(id)).isEqualTo(2);
        assertThat(transit(id)).hasSize(2);
        assertThat(balances()).containsExactly("a" + suffix + ":Maceta:18.000", "a" + suffix + ":Sustrato:5.000");

        // Two different keys at the same time on another transfer: one dispatch, one conflict, effects once.
        String second = approved();
        List<Integer> statuses = race(
            () -> originOperator.post("/transfers/" + second + "/dispatch", dispatchNow("Uno")).statusCode(),
            () -> originOperator.post("/transfers/" + second + "/dispatch", dispatchNow("Dos")).statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(movements(second)).isEqualTo(2);
        assertThat(transit(second)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers_event e JOIN transfers_transfer t ON t.id=e.transfer_id "
            + "WHERE t.external_id=?::uuid AND e.to_status='IN_TRANSIT'", Integer.class, second)).isEqualTo(1);
        assertThat(balances()).containsExactly("a" + suffix + ":Maceta:6.000", "a" + suffix + ":Sustrato:0.000");
    }

    @Test void onlyTheOriginOperatorDispatchesAnApprovedTransferWithValidData() throws Exception {
        String preparing = preparing();
        String id = approved();
        Snapshot before = snapshot(id);

        // Without an active route there is no dispatch.
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("Transportes Andinos")), 409, "ROUTE_REQUIRED");
        String route = read(created(route("AB", a, b, "HIGH"))).get("externalId").asText();
        admin.post("/logistics/routes/" + route + "/update", Map.of("code", "AB" + suffix, "priority", "HIGH", "active", false));
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("Transportes Andinos")), 409, "ROUTE_REQUIRED");
        admin.post("/logistics/routes/" + route + "/update", Map.of("code", "AB" + suffix, "priority", "HIGH", "active", true));

        // Roles and scope: managers, administrators, the destination and other branches do not dispatch.
        for (Browser profile : List.of(originManager, destinationManager, admin)) {
            assertThat(profile.post("/transfers/" + id + "/dispatch", dispatchNow("X")).statusCode()).isEqualTo(403);
        }
        assertError(destinationOperator.post("/transfers/" + id + "/dispatch", dispatchNow("X")), 403, "TRANSFER_FORBIDDEN");
        assertError(otherOperator.post("/transfers/" + id + "/dispatch", dispatchNow("X")), 404, "TRANSFER_NOT_FOUND");
        assertError(originOperator.post("/transfers/" + preparing + "/dispatch", dispatchNow("X")), 409, "TRANSFER_STATUS_CONFLICT");

        // Mandatory carrier, departure and estimated arrival, coherent in time.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(-5));
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatch("  ", now, now.plusHours(2))), 400, "VALIDATION_FAILED");
        assertError(originOperator.post("/transfers/" + id + "/dispatch", Map.of("carrier", "X", "departedAt", now.toString())),
            400, "VALIDATION_FAILED");
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatch("X", now, now.minusMinutes(1))), 400, "VALIDATION_FAILED");
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatch("X", now.plusHours(1), now.plusHours(3))),
            400, "VALIDATION_FAILED");
        // Beyond the clock tolerance a departure is future; the tolerance itself is checked by the valid dispatch below.
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatch("X", now.plusMinutes(10), now.plusHours(3))),
            400, "VALIDATION_FAILED");
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatch("X", now.minusDays(2), now.plusHours(3))),
            400, "VALIDATION_FAILED");
        assertError(originOperator.post("/transfers/" + id + "/dispatch", Map.of("carrier", "X", "departedAt", "mañana",
            "estimatedArrivalAt", now.toString())), 400, "VALIDATION_FAILED");
        Map<String, Object> extra = new HashMap<>(dispatchNow("X"));
        extra.put("lines", List.of());
        assertError(originOperator.post("/transfers/" + id + "/dispatch", extra), 400, "VALIDATION_FAILED");

        // Products are revalidated in both branches immediately before the dispatch.
        jdbc.update("UPDATE catalog_branch_product SET enabled=false WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) "
            + "AND product_id=(SELECT id FROM catalog_product WHERE external_id=?)", b, other);
        HttpResponse<String> disabled = originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("X"));
        assertError(disabled, 409, "PRODUCT_NOT_OPERABLE");
        assertThat(read(disabled).get("detail").asText()).startsWith("Línea 2:").contains("sucursal destino");
        jdbc.update("UPDATE catalog_branch_product SET enabled=true WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) "
            + "AND product_id=(SELECT id FROM catalog_product WHERE external_id=?)", b, other);
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", b);
        assertError(originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("X")), 409, "BRANCH_INACTIVE");
        jdbc.update("UPDATE identity_branch SET active=true WHERE external_id=?", b);
        assertThat(snapshot(id)).isEqualTo(before);

        // A transfer prepared entirely at zero is not dispatched (INC-015/D03).
        String zero = requested();
        assertThat(originOperator.post("/transfers/" + zero + "/prepare", prepared(1, "0", 2, "0")).statusCode()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + zero + "/approve", Map.of()).statusCode()).isEqualTo(200);
        assertError(originOperator.post("/transfers/" + zero + "/dispatch", dispatchNow("X")), 409, "NOTHING_TO_DISPATCH");

        // The valid attempt still succeeds after every rejection, even from a device clock 3 minutes ahead of the server.
        OffsetDateTime ahead = OffsetDateTime.now(ZoneOffset.ofHours(-5)).plusMinutes(3);
        HttpResponse<String> done = originOperator.post("/transfers/" + id + "/dispatch", dispatch("Transportes Andinos", ahead,
            ahead.plusHours(4)));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
    }

    @Test void aLineAtZeroIsKeptWithoutMovementAndADepletingExitCarriesItsRoundingIntoTransit() throws Exception {
        created(route("AB", a, b, "HIGH"));
        // 3 units valued 1: average 0.333333; sending all 3 withdraws the whole value, 0.999999 + 0.000001.
        balance(other, "3", "1", "0.333333");
        String id = requestedWith(line(product, "caja", "1"), line(other, "unidad", "3"));
        assertThat(originOperator.post("/transfers/" + id + "/prepare", prepared(1, "0", 2, "3")).statusCode()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);

        HttpResponse<String> done = originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("Mensajería"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        assertThat(read(done).at("/lines/0/dispatchedQuantity").asText()).isEqualTo("0.000");
        assertThat(movements(id)).isEqualTo(1);
        assertThat(transit(id)).containsExactly("3.000|0.999999000000000000|0.000001000000000000");
        assertThat(jdbc.queryForList("SELECT dl.dispatched_base_qty||'|'||dl.base_value||'|'||dl.rounding_value "
            + "FROM logistics_dispatch_line dl JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer t "
            + "ON t.id=l.transfer_id WHERE t.external_id=?::uuid ORDER BY l.line_no", String.class, id))
            .containsExactly("0.000|0.000000000000000000|0.000000000000000000", "3.000|0.999999000000000000|0.000001000000000000");
        assertThat(jdbc.queryForObject("SELECT physical_qty||'|'||inventory_value FROM inventory_balance "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) "
            + "AND product_id=(SELECT id FROM catalog_product WHERE external_id=?)", String.class, a, other))
            .isEqualTo("0.000|0.000000000000000000");
    }

    @Test void eachDepletingLineKeepsItsOwnRoundingWhateverTheProductOrder() throws Exception {
        created(route("AB", a, b, "HIGH"));
        // Line 1 is the product created last: the ledger locks in product order, the lines follow the transfer.
        balance(other, "3", "1", "0.333333");   // 3 × 0.333333 = 0.999999: rounding +0.000001
        balance(product, "3", "2", "0.666667"); // 3 × 0.666667 = 2.000001: rounding −0.000001
        String id = requestedWith(line(other, "unidad", "3"), line(product, "unidad", "3"));
        assertThat(originOperator.post("/transfers/" + id + "/prepare", prepared(1, "3", 2, "3")).statusCode()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);

        HttpResponse<String> done = originOperator.post("/transfers/" + id + "/dispatch", dispatchNow("Mensajería"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT dl.base_value||'|'||dl.rounding_value FROM logistics_dispatch_line dl "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer t ON t.id=l.transfer_id "
            + "WHERE t.external_id=?::uuid ORDER BY l.line_no", String.class, id))
            .containsExactly("0.999999000000000000|0.000001000000000000", "2.000001000000000000|-0.000001000000000000");
        assertThat(transit(id)).containsExactly("3.000|0.999999000000000000|0.000001000000000000",
            "3.000|2.000001000000000000|-0.000001000000000000");
    }

    // ---------------------------------------------------------------- helpers

    /** Overwrites the origin balance of a product to set up a valuation edge case. */
    void balance(UUID item, String quantity, String value, String average) {
        jdbc.update("UPDATE inventory_balance SET physical_qty=?::numeric,inventory_value=?::numeric,average_base_cost=?::numeric "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) "
            + "AND product_id=(SELECT id FROM catalog_product WHERE external_id=?)", quantity, value, average, a, item);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    /** An indivisible product with base unit "unidad" and commercial unit "caja" of 12, enabled in the given branches. */
    UUID product(String name, UUID... branches) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,'unidad',false)",
            id, "SKU-" + UUID.randomUUID(), name);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'caja',12,false,false FROM catalog_product WHERE external_id=?", id);
        for (UUID branch : branches) {
            jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty) SELECT b.id,p.id,true,0 "
                + "FROM identity_branch b CROSS JOIN catalog_product p WHERE b.external_id=? AND p.external_id=?", branch, id);
        }
        return id;
    }

    Browser account(String role, UUID branch) throws Exception {
        String code = role + UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
                + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)",
            UUID.randomUUID(), code, "Persona " + role, passwords.encode("private individual password"), role, branch);
        Browser browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password"), null).statusCode())
            .isEqualTo(200);
        return browser;
    }

    /** Valued entry of {@code quantity} base units at cost 5 in the origin, posted through the inventory ledger. */
    void stock(UUID item, String quantity) throws Exception {
        HttpResponse<String> posted = originOperator.post("/inventory/movements", Map.of("kind", "MANUAL_ENTRY",
            "product", item.toString(), "unit", "unidad", "quantity", quantity, "reason", "Inventario inicial", "unitCost", "5"));
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    HttpResponse<String> route(String code, UUID origin, UUID destination, String priority) throws Exception {
        return route(admin, code, origin, destination, priority);
    }

    HttpResponse<String> route(Browser by, String code, UUID origin, UUID destination, String priority) throws Exception {
        return by.post("/logistics/routes", Map.of("code", code + suffix, "origin", origin.toString(),
            "destination", destination.toString(), "priority", priority));
    }

    HttpResponse<String> created(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return response;
    }

    static Map<String, Object> line(UUID item, String unit, String quantity) {
        return Map.of("product", item.toString(), "unit", unit, "quantity", quantity);
    }

    static Map<String, Object> prepared(Object... lineAndQuantity) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < lineAndQuantity.length; i += 2) lines.add(Map.of("lineNo", lineAndQuantity[i], "quantity", lineAndQuantity[i + 1]));
        return Map.of("lines", lines);
    }

    static Map<String, Object> dispatch(String carrier, OffsetDateTime departed, OffsetDateTime estimated) {
        return Map.of("carrier", carrier, "departedAt", departed.toString(), "estimatedArrivalAt", estimated.toString());
    }

    static Map<String, Object> dispatchNow(String carrier) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(-5));
        return dispatch(carrier, now, now.plus(Duration.ofHours(4)));
    }

    String requestedWith(Map<?, ?>... lines) throws Exception {
        HttpResponse<String> created = destinationOperator.post("/transfers", Map.of("origin", a.toString(), "lines", List.of(lines)));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    /** A request from A to B of 2 boxes of the product and 5 units of the other one. */
    String requested() throws Exception {
        return requestedWith(line(product, "caja", "2"), line(other, "unidad", "5"));
    }

    /** Prepared at 1 box (12 units) and 5 units. */
    String preparing() throws Exception {
        String id = requested();
        HttpResponse<String> done = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        return id;
    }

    String approved() throws Exception {
        String id = preparing();
        HttpResponse<String> done = originManager.post("/transfers/" + id + "/approve", Map.of());
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        return id;
    }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(read(response).path("code").asText()).as(response.body()).isEqualTo(code);
    }

    List<String> codes(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return read(response).findValuesAsString("code").stream().filter(code -> code.endsWith(suffix)
            && !code.startsWith("a") && !code.startsWith("b") && !code.startsWith("c")).toList();
    }

    int routes() {
        return jdbc.queryForObject("SELECT count(*) FROM logistics_route", Integer.class);
    }

    /** Physical quantities of the test's products in A, B and C: B must never receive anything before the arrival. */
    List<String> balances() {
        return jdbc.queryForList("SELECT b.code||':'||p.name||':'||ib.physical_qty FROM inventory_balance ib "
            + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
            + "WHERE b.external_id IN (?,?,?) ORDER BY 1", String.class, a, b, c);
    }

    /** pending quantity|value|rounding of each transit balance of a transfer, by line. */
    List<String> transit(String id) {
        return jdbc.queryForList("SELECT it.pending_base_qty||'|'||it.pending_base_value||'|'||it.pending_rounding_value "
            + "FROM inventory_transit it JOIN logistics_dispatch_line dl ON dl.id=it.dispatch_line_id "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer t ON t.id=l.transfer_id "
            + "WHERE t.external_id=?::uuid ORDER BY l.line_no", String.class, id);
    }

    int movements(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movement m JOIN logistics_dispatch_line dl ON dl.id=m.dispatch_line_id "
            + "JOIN logistics_dispatch d ON d.id=dl.dispatch_id JOIN transfers_transfer t ON t.id=d.transfer_id "
            + "WHERE t.external_id=?::uuid", Integer.class, id);
    }

    record Snapshot(String transfer, List<String> balances, int movements, int dispatches, int transit, int operations) {}

    /** Everything a rejected dispatch must leave untouched. */
    Snapshot snapshot(String id) {
        return new Snapshot(jdbc.queryForObject("SELECT t.status||'|'||(SELECT count(*) FROM transfers_event e WHERE e.transfer_id=t.id) "
                + "FROM transfers_transfer t WHERE t.external_id=?::uuid", String.class, id), balances(),
            jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class),
            jdbc.queryForObject("SELECT count(*) FROM logistics_dispatch", Integer.class),
            jdbc.queryForObject("SELECT count(*) FROM inventory_transit", Integer.class),
            jdbc.queryForObject("SELECT count(*) FROM inventory_operation", Integer.class));
    }

    /** Runs the calls at the same time and returns their HTTP statuses. */
    @SafeVarargs
    static List<Integer> race(Callable<Integer>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get());
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        /** POST with a fresh idempotency key. */
        HttpResponse<String> post(String path, Object body) throws Exception { return post(path, body, UUID.randomUUID().toString()); }
        HttpResponse<String> post(String path, Object body, String key) throws Exception {
            JsonNode csrf = json.readTree(get("/auth/csrf").body());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf.has("headerName")) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            if (key != null) request.header("Idempotency-Key", key);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
