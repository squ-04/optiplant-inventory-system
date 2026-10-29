package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * RF-0032–RF-0034: transfer request by the destination operator, preparation by the origin operator and approval,
 * rejection or cancellation by the authorized actor. No step reserves or moves stock; each transition is atomic, applied
 * once under a row lock and replayed, not repeated, with the same idempotency key.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=transfers-initial", "app.admin-password=private initial password"})
class TransferHttpTest {
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
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    /** a: origin, b: destination, c: an unrelated branch. */
    UUID a, b, c, product, other, onlyDestination;
    String suffix;
    Browser admin, originOperator, originManager, destinationOperator, destinationManager, otherOperator, otherManager;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString();
        a = branch("a");
        b = branch("b");
        c = branch("c");
        product = product("Maceta", a, b, c);
        other = product("Sustrato", a, b);
        onlyDestination = product("Abono", b);
        admin = account("ADMIN", null);
        originOperator = account("OPERATOR", a);
        originManager = account("MANAGER", a);
        destinationOperator = account("OPERATOR", b);
        destinationManager = account("MANAGER", b);
        otherOperator = account("OPERATOR", c);
        otherManager = account("MANAGER", c);
        stock(product, "30");
        stock(other, "10");
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

    static Map<String, Object> line(UUID item, String unit, String quantity) {
        return Map.of("product", item.toString(), "unit", unit, "quantity", quantity);
    }

    Map<String, Object> request(UUID origin, List<?> lines) {
        return Map.of("origin", origin.toString(), "lines", lines);
    }

    static Map<String, Object> prepared(Object... lineAndQuantity) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < lineAndQuantity.length; i += 2) lines.add(Map.of("lineNo", lineAndQuantity[i], "quantity", lineAndQuantity[i + 1]));
        return Map.of("lines", lines);
    }

    /** A request from A to B of 2 boxes of the product and 5 units of the other one. */
    JsonNode requested() throws Exception {
        HttpResponse<String> created = destinationOperator.post("/transfers",
            request(a, List.of(line(product, "caja", "2"), line(other, "unidad", "5"))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created);
    }

    String preparing() throws Exception {
        String id = requested().get("externalId").asText();
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

    static String code(HttpResponse<String> response) {
        return new JsonMapper().readTree(response.body()).path("code").asText();
    }

    /** physical quantities of every balance of the test's products in A, B and C. */
    List<String> balances() {
        return jdbc.queryForList("SELECT b.code||':'||p.name||':'||ib.physical_qty FROM inventory_balance ib "
            + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
            + "WHERE b.external_id IN (?,?,?) ORDER BY 1", String.class, a, b, c);
    }

    /** Movements of the test's branches plus transit rows: transfers must never add any before dispatch. */
    int operations() {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movement m JOIN identity_branch b ON b.id=m.branch_id "
            + "WHERE b.external_id IN (?,?,?)", Integer.class, a, b, c)
            + jdbc.queryForObject("SELECT count(*) FROM inventory_transit", Integer.class);
    }

    int transfers() {
        return jdbc.queryForObject("SELECT count(*) FROM transfers_transfer t JOIN identity_branch d ON d.id=t.destination_branch_id "
            + "WHERE d.external_id IN (?,?,?)", Integer.class, a, b, c);
    }

    /** status, prepared quantities and event count of a transfer, to prove a rejected attempt changed nothing. */
    String state(String id) {
        return jdbc.queryForObject("SELECT t.status||'|'||(SELECT string_agg(coalesce(l.prepared_commercial_qty::text,'-'),',' "
            + "ORDER BY l.line_no) FROM transfers_line l WHERE l.transfer_id=t.id)||'|'||(SELECT count(*) FROM transfers_event e "
            + "WHERE e.transfer_id=t.id) FROM transfers_transfer t WHERE t.external_id=?::uuid", String.class, id);
    }

    @Test void destinationRequestsOriginPreparesAndManagerApprovesWithoutMovingStock() throws Exception {
        List<String> stock = balances();
        int operations = operations();

        // RF-0032-CA-01: the request is consultable with product, quantity, origin and destination.
        JsonNode created = requested();
        String id = created.get("externalId").asText();
        assertThat(created.get("code").asText()).matches("TR-\\d{6}");
        assertThat(created.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(created.at("/origin/externalId").asText()).isEqualTo(a.toString());
        assertThat(created.at("/destination/externalId").asText()).isEqualTo(b.toString());
        assertThat(created.at("/lines/0/requestedQuantity").asText()).isEqualTo("2.000");
        assertThat(created.at("/lines/0/requestedBaseQuantity").asText()).isEqualTo("24.000");
        assertThat(created.at("/lines/0/unit").asText()).isEqualTo("caja");
        assertThat(created.at("/lines/1/requestedQuantity").asText()).isEqualTo("5.000");
        assertThat(created.at("/lines/0/preparedQuantity").isNull()).isTrue();
        // The client applies the same unit rules as the server when preparing.
        assertThat(created.at("/lines/0/unitDivisible").asBoolean(true)).isFalse();
        assertThat(created.at("/lines/0/baseDivisible").asBoolean(true)).isFalse();
        assertThat(created.get("approval").isNull()).isTrue();
        // RF-0032-CA-02: a valid multi-line request keeps its quantities without reserving them.
        assertThat(balances()).isEqualTo(stock);
        assertThat(operations()).isEqualTo(operations);

        // The destination does not see the origin's stock; the origin reviews it to prepare (RF-0033-CA-01).
        JsonNode destinationView = read(destinationOperator.get("/transfers/" + id));
        assertThat(destinationView.at("/lines/0/originAvailableQuantity").isNull()).isTrue();
        JsonNode originView = read(originOperator.get("/transfers/" + id));
        assertThat(originView.at("/lines/0/originAvailableQuantity").asText()).isEqualTo("30.000");
        assertThat(originView.at("/lines/1/originAvailableQuantity").asText()).isEqualTo("10.000");

        // RF-0033-CA-02: the origin lowers line 1; requested and prepared quantities stay distinguishable.
        HttpResponse<String> prepared = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5"));
        assertThat(prepared.statusCode()).as(prepared.body()).isEqualTo(200);
        assertThat(read(prepared).get("status").asText()).isEqualTo("PREPARING");
        JsonNode seen = read(destinationOperator.get("/transfers/" + id));
        assertThat(seen.at("/lines/0/requestedQuantity").asText()).isEqualTo("2.000");
        assertThat(seen.at("/lines/0/preparedQuantity").asText()).isEqualTo("1.000");
        assertThat(seen.at("/lines/0/preparedBaseQuantity").asText()).isEqualTo("12.000");
        assertThat(seen.at("/lines/1/preparedQuantity").asText()).isEqualTo("5.000");

        // A revision while preparing is a new event with its own snapshot; earlier snapshots are kept.
        HttpResponse<String> revised = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "2", 2, "0"));
        assertThat(revised.statusCode()).as(revised.body()).isEqualTo(200);
        JsonNode events = read(revised).get("events");
        assertThat(events).hasSize(3);
        assertThat(events.get(1).at("/preparation/0/quantity").asText()).isEqualTo("1.000");
        assertThat(events.get(2).get("fromStatus").asText()).isEqualTo("PREPARING");
        assertThat(events.get(2).at("/preparation/1/quantity").asText()).isEqualTo("0.000");

        // RF-0034-CA-01: the approval is associated with the transfer and consultable.
        HttpResponse<String> approvedResponse = originManager.post("/transfers/" + id + "/approve", Map.of());
        assertThat(approvedResponse.statusCode()).as(approvedResponse.body()).isEqualTo(200);
        JsonNode approved = read(destinationManager.get("/transfers/" + id));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.at("/approval/actor/fullName").asText()).isEqualTo("Persona MANAGER");
        assertThat(approved.at("/events/3/toStatus").asText()).isEqualTo("APPROVED");

        // INC-015/D05: no step before dispatch reserves or moves stock.
        assertThat(balances()).isEqualTo(stock);
        assertThat(operations()).isEqualTo(operations);
    }

    @Test void invalidRequestsAreRejectedWithoutCreatingTransfersOrTouchingStock() throws Exception {
        List<String> stock = balances();
        int before = transfers();

        HttpResponse<String> same = destinationOperator.post("/transfers", request(b, List.of(line(product, "caja", "1"))));
        assertThat(same.statusCode()).isEqualTo(400);
        assertThat(code(same)).isEqualTo("SAME_BRANCH");

        // The second line is not enabled in the origin, so the valid first line is not requested either.
        HttpResponse<String> notInOrigin = destinationOperator.post("/transfers",
            request(a, List.of(line(product, "caja", "1"), line(onlyDestination, "unidad", "1"))));
        assertThat(notInOrigin.statusCode()).isEqualTo(409);
        assertThat(code(notInOrigin)).isEqualTo("PRODUCT_NOT_OPERABLE");
        assertThat(read(notInOrigin).get("detail").asText()).startsWith("Línea 2:").contains("sucursal origen");

        // A product the destination does not operate.
        UUID onlyOrigin = product("Semilla", a);
        HttpResponse<String> notInDestination = destinationOperator.post("/transfers", request(a, List.of(line(onlyOrigin, "unidad", "1"))));
        assertThat(code(notInDestination)).isEqualTo("PRODUCT_NOT_OPERABLE");

        HttpResponse<String> unit = destinationOperator.post("/transfers", request(a, List.of(line(product, "bulto", "1"))));
        assertThat(unit.statusCode()).isEqualTo(404);
        assertThat(code(unit)).isEqualTo("PRODUCT_UNIT_NOT_FOUND");

        HttpResponse<String> fraction = destinationOperator.post("/transfers", request(a, List.of(line(product, "caja", "1.5"))));
        assertThat(fraction.statusCode()).isEqualTo(400);

        HttpResponse<String> repeated = destinationOperator.post("/transfers",
            request(a, List.of(line(product, "caja", "1"), line(product, "caja", "2"))));
        assertThat(repeated.statusCode()).isEqualTo(400);
        assertThat(read(repeated).get("detail").asText()).contains("repite el producto y la unidad de la línea 1");

        HttpResponse<String> urgency = destinationOperator.post("/transfers",
            Map.of("origin", a.toString(), "lines", List.of(line(product, "caja", "1")), "urgent", true));
        assertThat(urgency.statusCode()).isEqualTo(400);

        // Only the destination operator requests: the administrator and managers are rejected.
        HttpResponse<String> byAdmin = admin.post("/transfers", request(a, List.of(line(product, "caja", "1"))));
        assertThat(byAdmin.statusCode()).isEqualTo(403);
        assertThat(code(byAdmin)).isEqualTo("TRANSFER_FORBIDDEN");
        assertThat(destinationManager.post("/transfers", request(a, List.of(line(product, "caja", "1")))).statusCode()).isEqualTo(403);

        // Inactive origin, then inactive destination.
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        HttpResponse<String> inactiveOrigin = destinationOperator.post("/transfers", request(a, List.of(line(product, "caja", "1"))));
        assertThat(inactiveOrigin.statusCode()).isEqualTo(409);
        assertThat(code(inactiveOrigin)).isEqualTo("BRANCH_INACTIVE");
        assertThat(read(destinationOperator.get("/transfers/origins")).findValuesAsString("externalId")).doesNotContain(a.toString());
        jdbc.update("UPDATE identity_branch SET active=true WHERE external_id=?", a);
        assertThat(read(destinationOperator.get("/transfers/origins")).findValuesAsString("externalId"))
            .contains(a.toString(), c.toString()).doesNotContain(b.toString());
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", b);
        HttpResponse<String> inactiveDestination = destinationOperator.post("/transfers", request(a, List.of(line(product, "caja", "1"))));
        assertThat(code(inactiveDestination)).isEqualTo("BRANCH_INACTIVE");
        jdbc.update("UPDATE identity_branch SET active=true WHERE external_id=?", b);

        assertThat(transfers()).isEqualTo(before);
        assertThat(balances()).isEqualTo(stock);
    }

    @Test void preparationNeverExceedsTheRequestAndRejectedAttemptsChangeNothing() throws Exception {
        String id = requested().get("externalId").asText();
        String before = state(id);
        assertThat(before).isEqualTo("REQUESTED|-,-|1");

        // INC-015/D03: never more than requested; line 2 is valid but nothing is applied.
        HttpResponse<String> more = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "3", 2, "5"));
        assertThat(more.statusCode()).isEqualTo(409);
        assertThat(code(more)).isEqualTo("PREPARED_EXCEEDS_REQUESTED");
        assertThat(state(id)).isEqualTo(before);

        HttpResponse<String> missing = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "1"));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(originOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5", 3, "1")).statusCode()).isEqualTo(400);
        assertThat(originOperator.post("/transfers/" + id + "/prepare", prepared(1, "0.5", 2, "5")).statusCode()).isEqualTo(400);
        assertThat(originOperator.post("/transfers/" + id + "/prepare", prepared(1, "-1", 2, "5")).statusCode()).isEqualTo(400);

        // Only the origin operator prepares.
        HttpResponse<String> byDestination = destinationOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5"));
        assertThat(byDestination.statusCode()).isEqualTo(403);
        assertThat(originManager.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5")).statusCode()).isEqualTo(403);
        assertThat(admin.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5")).statusCode()).isEqualTo(403);
        assertThat(otherOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5")).statusCode()).isEqualTo(404);
        assertThat(state(id)).isEqualTo(before);

        // Zero is a valid proposal for one line; the request remains for every line.
        HttpResponse<String> zero = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "0", 2, "5"));
        assertThat(zero.statusCode()).as(zero.body()).isEqualTo(200);
        assertThat(state(id)).isEqualTo("PREPARING|0.000,5.000|2");

        // An approved transfer is no longer prepared.
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);
        HttpResponse<String> late = originOperator.post("/transfers/" + id + "/prepare", prepared(1, "1", 2, "5"));
        assertThat(code(late)).isEqualTo("TRANSFER_STATUS_CONFLICT");
    }

    @Test void onlyTheOriginManagerRejectsWithAReasonWhilePreparing() throws Exception {
        int operations = operations();
        List<String> stock = balances();
        // RF-0034-CA-04: not from «solicitada».
        String requestedId = requested().get("externalId").asText();
        HttpResponse<String> early = originManager.post("/transfers/" + requestedId + "/reject", Map.of("reason", "Sin existencias"));
        assertThat(early.statusCode()).isEqualTo(409);
        assertThat(state(requestedId)).isEqualTo("REQUESTED|-,-|1");

        String id = preparing();
        String before = state(id);
        Map<String, Object> reason = Map.of("reason", "Existencias comprometidas para la temporada");
        assertThat(code(originOperator.post("/transfers/" + id + "/reject", reason))).isEqualTo("TRANSFER_FORBIDDEN");
        assertThat(admin.post("/transfers/" + id + "/reject", reason).statusCode()).isEqualTo(403);
        // The destination manager does not obtain the permission by supervising the receipt.
        HttpResponse<String> byDestination = destinationManager.post("/transfers/" + id + "/reject", reason);
        assertThat(byDestination.statusCode()).isEqualTo(403);
        assertThat(otherManager.post("/transfers/" + id + "/reject", reason).statusCode()).isEqualTo(404);
        HttpResponse<String> noReason = originManager.post("/transfers/" + id + "/reject", Map.of("reason", "   "));
        assertThat(noReason.statusCode()).isEqualTo(400);
        assertThat(code(noReason)).isEqualTo("REASON_REQUIRED");
        assertThat(originManager.post("/transfers/" + id + "/reject", Map.of()).statusCode()).isEqualTo(400);
        assertThat(state(id)).isEqualTo(before);

        // RF-0034-CA-03: rejected with consultable reason and responsible, quantities untouched, no approval.
        HttpResponse<String> rejected = originManager.post("/transfers/" + id + "/reject", reason);
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(200);
        JsonNode detail = read(destinationOperator.get("/transfers/" + id));
        assertThat(detail.get("status").asText()).isEqualTo("REJECTED");
        assertThat(detail.get("approval").isNull()).isTrue();
        JsonNode last = detail.get("events").get(detail.get("events").size() - 1);
        assertThat(last.get("reason").asText()).isEqualTo("Existencias comprometidas para la temporada");
        assertThat(last.at("/actor/fullName").asText()).isEqualTo("Persona MANAGER");
        assertThat(state(id)).isEqualTo("REJECTED|1.000,5.000|3");

        // Terminal: neither approved nor rejected again.
        assertThat(code(originManager.post("/transfers/" + id + "/approve", Map.of()))).isEqualTo("TRANSFER_STATUS_CONFLICT");
        assertThat(code(originManager.post("/transfers/" + id + "/reject", reason))).isEqualTo("TRANSFER_STATUS_CONFLICT");

        // Not from «aprobada» nor «en tránsito».
        String approvedId = approved();
        assertThat(originManager.post("/transfers/" + approvedId + "/reject", reason).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE transfers_transfer SET status='IN_TRANSIT' WHERE external_id=?::uuid", approvedId);
        assertThat(originManager.post("/transfers/" + approvedId + "/reject", reason).statusCode()).isEqualTo(409);
        assertThat(balances()).isEqualTo(stock);
        assertThat(operations()).isEqualTo(operations);
    }

    @Test void destinationCancelsBeforeApprovalAndOnlyTheOriginManagerAfterIt() throws Exception {
        int operations = operations();
        List<String> stock = balances();
        // Before approval: the destination operator cancels, with an optional reason.
        String requestedId = requested().get("externalId").asText();
        assertThat(originManager.post("/transfers/" + requestedId + "/cancel", Map.of("reason", "x")).statusCode()).isEqualTo(403);
        assertThat(destinationManager.post("/transfers/" + requestedId + "/cancel", Map.of()).statusCode()).isEqualTo(403);
        assertThat(admin.post("/transfers/" + requestedId + "/cancel", Map.of()).statusCode()).isEqualTo(403);
        HttpResponse<String> cancelled = destinationOperator.post("/transfers/" + requestedId + "/cancel", Map.of());
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
        assertThat(read(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(code(destinationOperator.post("/transfers/" + requestedId + "/cancel", Map.of()))).isEqualTo("TRANSFER_STATUS_CONFLICT");

        String preparingId = preparing();
        HttpResponse<String> whilePreparing = destinationOperator.post("/transfers/" + preparingId + "/cancel",
            Map.of("reason", "Ya no se necesita"));
        assertThat(whilePreparing.statusCode()).isEqualTo(200);
        assertThat(state(preparingId)).isEqualTo("CANCELLED|1.000,5.000|3");

        // After approval and before dispatch: only the origin manager, with a reason.
        String id = approved();
        String before = state(id);
        assertThat(code(destinationOperator.post("/transfers/" + id + "/cancel", Map.of("reason", "x")))).isEqualTo("TRANSFER_FORBIDDEN");
        assertThat(originOperator.post("/transfers/" + id + "/cancel", Map.of("reason", "x")).statusCode()).isEqualTo(403);
        assertThat(code(originManager.post("/transfers/" + id + "/cancel", Map.of()))).isEqualTo("REASON_REQUIRED");
        assertThat(state(id)).isEqualTo(before);
        HttpResponse<String> byManager = originManager.post("/transfers/" + id + "/cancel", Map.of("reason", "Vehículo no disponible"));
        assertThat(byManager.statusCode()).as(byManager.body()).isEqualTo(200);
        JsonNode events = read(byManager).get("events");
        assertThat(events.get(events.size() - 1).get("reason").asText()).isEqualTo("Vehículo no disponible");

        // In transit it is never cancelled; neither the transfer nor the inventory changes.
        String transit = approved();
        jdbc.update("UPDATE transfers_transfer SET status='IN_TRANSIT' WHERE external_id=?::uuid", transit);
        String inTransit = state(transit);
        HttpResponse<String> refused = originManager.post("/transfers/" + transit + "/cancel", Map.of("reason", "x"));
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(code(refused)).isEqualTo("TRANSFER_IN_TRANSIT");
        assertThat(state(transit)).isEqualTo(inTransit);
        assertThat(balances()).isEqualTo(stock);
        assertThat(operations()).isEqualTo(operations);
    }

    @Test void repeatedAndConcurrentRequestsApplyEachTransitionOnce() throws Exception {
        int operations = operations();
        // The same key returns the original transfer without creating another.
        int before = transfers();
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = request(a, List.of(line(product, "caja", "1")));
        HttpResponse<String> first = destinationOperator.post("/transfers", body, key);
        HttpResponse<String> retry = destinationOperator.post("/transfers", body, key);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(read(retry).get("externalId").asText()).isEqualTo(read(first).get("externalId").asText());
        assertThat(transfers()).isEqualTo(before + 1);
        HttpResponse<String> reused = destinationOperator.post("/transfers", request(a, List.of(line(product, "caja", "2"))), key);
        assertThat(code(reused)).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(transfers()).isEqualTo(before + 1);
        assertThat(destinationOperator.post("/transfers", body, null).statusCode()).isEqualTo(400);

        // A repeated approval with its key is replayed; with a new key it is a conflict. One approval event only.
        String id = preparing();
        String approveKey = UUID.randomUUID().toString();
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of(), approveKey).statusCode()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of(), approveKey).statusCode()).isEqualTo(200);
        assertThat(code(originManager.post("/transfers/" + id + "/approve", Map.of()))).isEqualTo("TRANSFER_STATUS_CONFLICT");
        assertThat(events(id, "APPROVED")).isEqualTo(1);

        // Preparation, rejection and cancellation are replayed with their key, never applied twice.
        String replayed = requested().get("externalId").asText();
        String prepareKey = UUID.randomUUID().toString();
        assertThat(originOperator.post("/transfers/" + replayed + "/prepare", prepared(1, "1", 2, "5"), prepareKey).statusCode()).isEqualTo(200);
        assertThat(originOperator.post("/transfers/" + replayed + "/prepare", prepared(1, "1", 2, "5"), prepareKey).statusCode()).isEqualTo(200);
        assertThat(code(originOperator.post("/transfers/" + replayed + "/prepare", prepared(1, "2", 2, "5"), prepareKey)))
            .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(events(replayed, "PREPARING")).isEqualTo(1);
        String rejectKey = UUID.randomUUID().toString();
        Map<String, Object> reason = Map.of("reason", "Sin existencias");
        assertThat(originManager.post("/transfers/" + replayed + "/reject", reason, rejectKey).statusCode()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + replayed + "/reject", reason, rejectKey).statusCode()).isEqualTo(200);
        assertThat(events(replayed, "REJECTED")).isEqualTo(1);
        String cancelled = requested().get("externalId").asText();
        String cancelKey = UUID.randomUUID().toString();
        assertThat(destinationOperator.post("/transfers/" + cancelled + "/cancel", Map.of(), cancelKey).statusCode()).isEqualTo(200);
        assertThat(destinationOperator.post("/transfers/" + cancelled + "/cancel", Map.of(), cancelKey).statusCode()).isEqualTo(200);
        assertThat(events(cancelled, "CANCELLED")).isEqualTo(1);

        // Simultaneous approval and rejection: exactly one transition wins; the other changes nothing.
        String raced = preparing();
        List<Integer> statuses = race(
            () -> originManager.post("/transfers/" + raced + "/approve", Map.of()).statusCode(),
            () -> originManager.post("/transfers/" + raced + "/reject", Map.of("reason", "Sin vehículo")).statusCode(),
            () -> originManager.post("/transfers/" + raced + "/approve", Map.of()).statusCode());
        assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
        assertThat(events(raced, "APPROVED") + events(raced, "REJECTED")).isEqualTo(1);

        // Simultaneous destination cancellation and preparation: the loser sees the winner's state.
        String contested = requested().get("externalId").asText();
        List<Integer> contestedStatuses = race(
            () -> destinationOperator.post("/transfers/" + contested + "/cancel", Map.of()).statusCode(),
            () -> originOperator.post("/transfers/" + contested + "/prepare", prepared(1, "1", 2, "5")).statusCode());
        assertThat(contestedStatuses).contains(200);
        String status = jdbc.queryForObject("SELECT status FROM transfers_transfer WHERE external_id=?::uuid", String.class, contested);
        assertThat(events(contested, "CANCELLED")).isEqualTo(status.equals("CANCELLED") ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT sequence_no)=count(*) FROM transfers_event e JOIN transfers_transfer t "
            + "ON t.id=e.transfer_id WHERE t.external_id=?::uuid", Boolean.class, contested)).isTrue();
        assertThat(operations()).isEqualTo(operations);
    }

    @Test void stateHistoryIsAppendOnlyForTheApplication() throws Exception {
        String id = requested().get("externalId").asText();
        assertThatThrownBy(() -> jdbc.update("UPDATE transfers_event SET reason='x' WHERE transfer_id="
            + "(SELECT id FROM transfers_transfer WHERE external_id=?::uuid)", id)).hasStackTraceContaining("permission denied for table transfers_event");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transfers_event WHERE transfer_id="
            + "(SELECT id FROM transfers_transfer WHERE external_id=?::uuid)", id)).hasStackTraceContaining("permission denied for table transfers_event");
    }

    @Test void eachProfileConsultsTheTransfersOfItsScope() throws Exception {
        String id = requested().get("externalId").asText();

        JsonNode incoming = read(destinationOperator.get("/transfers?direction=INCOMING"));
        assertThat(incoming.findValuesAsString("externalId")).contains(id);
        assertThat(incoming.at("/branch/externalId").asText()).isEqualTo(b.toString());
        assertThat(read(destinationOperator.get("/transfers?direction=OUTGOING")).findValuesAsString("externalId")).doesNotContain(id);
        assertThat(read(originManager.get("/transfers?direction=OUTGOING&status=REQUESTED")).findValuesAsString("externalId")).contains(id);
        assertThat(read(admin.get("/transfers")).findValuesAsString("externalId")).contains(id);
        assertThat(read(admin.get("/transfers?branch=" + a)).findValuesAsString("externalId")).contains(id);
        assertThat(read(admin.get("/transfers/" + id)).at("/lines/0/originAvailableQuantity").asText()).isEqualTo("30.000");

        // Another branch neither lists nor opens it.
        assertThat(read(otherOperator.get("/transfers")).findValuesAsString("externalId")).doesNotContain(id);
        assertThat(otherOperator.get("/transfers?branch=" + a).statusCode()).isEqualTo(403);
        assertThat(otherManager.get("/transfers/" + id).statusCode()).isEqualTo(404);
        HttpResponse<String> unknown = destinationOperator.get("/transfers?status=CLOSED");
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(read(unknown).get("detail").asText()).contains("REQUESTED (solicitada)", "REJECTED (rechazada)");
        assertThat(admin.get("/transfers/origins").statusCode()).isEqualTo(403);
    }

    int events(String id, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM transfers_event e JOIN transfers_transfer t ON t.id=e.transfer_id "
            + "WHERE t.external_id=?::uuid AND e.to_status=?", Integer.class, id, status);
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
