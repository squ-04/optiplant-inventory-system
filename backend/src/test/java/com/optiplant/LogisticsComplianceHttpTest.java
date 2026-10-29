package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * RF-0040: each dispatched transfer compares its estimated arrival with the single confirmed arrival, without inventing
 * one and without measuring the alert resolution. RF-0042: the state follows the events and stays «Recibida con
 * discrepancia» after resolving the alert and writing off the transit. RF-0043: compliance = complete punctual arrivals /
 * confirmed arrivals of the period (America/Bogota), by branch as origin and destination and by route, with the overdue
 * transfers in transit apart; managers read only their branch, operators do not read the report. Every reading is
 * checked to leave the inventory, transit and transfers untouched. The fixtures move the dispatch and arrival instants
 * of their own transfers to a fixed day, so each test reads its own period.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=logistics-initial", "app.admin-password=private initial password"})
class LogisticsComplianceHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "optiplant_test_runtime");
        r.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }
    static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    /** a and c: origins, b: destination. */
    UUID a, b, c, product;
    String suffix;
    Browser admin, aOperator, aManager, bOperator, bManager, cOperator, cManager;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        c = branch("c");
        product = product();
        admin = account("ADMIN", null);
        aOperator = account("OPERATOR", a);
        aManager = account("MANAGER", a);
        bOperator = account("OPERATOR", b);
        bManager = account("MANAGER", b);
        cOperator = account("OPERATOR", c);
        cManager = account("MANAGER", c);
        stock(aOperator, "100");
        stock(cOperator, "100");
        route("AB", a, b, "HIGH");
        route("CB", c, b, "LOW");
    }

    // ---------------------------------------------------------------- RF-0040 / RF-0042

    @Test void aPartialArrivalShowsItsOwnTwoHoursAndKeepsItsStateAfterTheAlert() throws Exception {
        LocalDate day = LocalDate.of(2025, 3, 10);
        String id = inTransit(a, "10");
        JsonNode pending = read(bOperator.get("/transfers/" + id));
        assertThat(pending.get("status").asText()).isEqualTo("IN_TRANSIT");
        // Without arrival nothing is invented: no real time, no delivery kind, no punctuality.
        assertThat(pending.at("/timing/estimatedSeconds").asLong()).isEqualTo(4 * 3600);
        assertThat(pending.at("/timing/actualSeconds").isNull()).isTrue();
        assertThat(pending.at("/timing/delivery").isNull()).isTrue();
        assertThat(pending.at("/timing/onTime").isNull()).isTrue();
        assertThat(pending.at("/timing/overdue").asBoolean()).isFalse();

        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("7")).statusCode()).isEqualTo(200);
        // RF-0040-CA-02: dispatched at 10:00, single partial reception confirmed at 12:00 the same day.
        times(id, at(day, 10, 0), at(day, 11, 0), at(day, 12, 0));
        JsonNode received = read(bManager.get("/transfers/" + id));
        assertThat(received.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(received.get("timing")).isEqualTo(json.readTree(
            "{\"estimatedSeconds\":3600,\"actualSeconds\":7200,\"deviationSeconds\":3600,\"delivery\":\"DISCREPANCY\","
                + "\"onTime\":false,\"overdue\":false}"));
        // RF-0040-CA-01: both values belong to the same transfer.
        assertThat(OffsetDateTime.parse(received.at("/dispatch/estimatedArrivalAt").asText()).toInstant())
            .isEqualTo(at(day, 11, 0).toInstant());
        assertThat(OffsetDateTime.parse(received.at("/arrival/occurredAt").asText()).toInstant()).isEqualTo(at(day, 12, 0).toInstant());

        // RF-0042-CA-02: resolving the alert and writing off the transit apart change neither the state, nor the
        // historical quantities, nor the 2 hours; no second reception is opened.
        Snapshot before = snapshot();
        assertThat(aManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "CLAIM")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + id + "/discrepancy/transit-loss", Map.of("reason", "Mercancía extraviada",
            "evidence", "Acta 7")).statusCode()).isEqualTo(200);
        JsonNode after = read(bManager.get("/transfers/" + id));
        assertThat(after.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(after.get("timing")).isEqualTo(received.get("timing"));
        assertThat(after.at("/arrival/occurredAt")).isEqualTo(received.at("/arrival/occurredAt"));
        assertThat(after.at("/lines/0/receivedQuantity").asText()).isEqualTo("7.000");
        assertThat(after.at("/lines/0/missingQuantity").asText()).isEqualTo("3.000");
        assertThat(after.at("/discrepancy/status").asText()).isEqualTo("RESOLVED");
        assertThat(after.at("/discrepancy/transitLoss").isNull()).isFalse();
        assertThat(after.get("events").size()).isEqualTo(received.get("events").size());
        Snapshot regularized = snapshot();
        assertThat(regularized.balances()).isEqualTo(before.balances());
        assertThat(regularized.arrivals()).isEqualTo(before.arrivals());
        assertThat(regularized.transfers()).isEqualTo(before.transfers());
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("3")).statusCode()).isEqualTo(409);
    }

    @Test void theListingShowsEachStateWithItsTiming() throws Exception {
        String requested = requested(a, "1");
        String preparing = prepared(requested(a, "1"), "1");
        String inTransit = inTransit(a, "1");
        String complete = inTransit(a, "2");
        String partial = inTransit(a, "3");
        assertThat(bOperator.post("/transfers/" + complete + "/receive", quantities("2")).statusCode()).isEqualTo(200);
        assertThat(bOperator.post("/transfers/" + partial + "/receive", quantities("1")).statusCode()).isEqualTo(200);
        Snapshot before = snapshot();

        JsonNode listing = read(bManager.get("/transfers?direction=INCOMING"));
        Map<String, JsonNode> byId = new HashMap<>();
        listing.get("items").forEach(item -> byId.put(item.get("externalId").asText(), item));
        assertThat(byId.get(requested).get("status").asText()).isEqualTo("REQUESTED");
        assertThat(byId.get(preparing).get("status").asText()).isEqualTo("PREPARING");
        assertThat(byId.get(inTransit).get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(byId.get(complete).get("status").asText()).isEqualTo("RECEIVED_COMPLETE");
        assertThat(byId.get(partial).get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(byId.get(requested).get("timing").isNull()).isTrue();
        assertThat(byId.get(inTransit).at("/timing/actualSeconds").isNull()).isTrue();
        assertThat(byId.get(complete).at("/timing/delivery").asText()).isEqualTo("COMPLETE");
        assertThat(byId.get(complete).at("/timing/onTime").asBoolean()).isTrue();
        assertThat(byId.get(partial).at("/timing/delivery").asText()).isEqualTo("DISCREPANCY");
        assertThat(byId.get(partial).at("/timing/onTime").asBoolean()).isFalse();
        // RF-0042-CA-01: the partial one is filtered as received with discrepancy, never as complete or in transit.
        assertThat(ids(bManager.get("/transfers?status=RECEIVED_DISCREPANCY&direction=INCOMING"))).containsExactly(partial);
        assertThat(ids(bManager.get("/transfers?status=IN_TRANSIT&direction=INCOMING"))).containsExactly(inTransit);
        assertThat(ids(bManager.get("/transfers?status=RECEIVED_COMPLETE&direction=INCOMING"))).containsExactly(complete);
        // An operator consults the times of its own branch's transfers; another branch's transfer stays hidden.
        assertThat(read(bOperator.get("/transfers/" + complete)).at("/timing/delivery").asText()).isEqualTo("COMPLETE");
        assertThat(cOperator.get("/transfers/" + complete).statusCode()).isEqualTo(404);
        // Overdue only while nothing has arrived and the estimate has passed.
        times(inTransit, OffsetDateTime.now(BOGOTA).minusHours(3), OffsetDateTime.now(BOGOTA).minusHours(1), null);
        assertThat(read(bManager.get("/transfers/" + inTransit)).at("/timing/overdue").asBoolean()).isTrue();
        assertThat(snapshot()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- RF-0043

    @Test void theKpiCountsCompletePunctualArrivalsOverConfirmedArrivalsOfThePeriod() throws Exception {
        LocalDate day = LocalDate.of(2025, 4, 15);
        String punctual = inTransit(a, "1");
        String partial = inTransit(a, "10");
        String overdue = inTransit(a, "1");
        String nextDay = inTransit(a, "1");
        assertThat(bOperator.post("/transfers/" + punctual + "/receive", quantities("1")).statusCode()).isEqualTo(200);
        assertThat(bOperator.post("/transfers/" + partial + "/receive", quantities("7")).statusCode()).isEqualTo(200);
        assertThat(bOperator.post("/transfers/" + nextDay + "/receive", quantities("1")).statusCode()).isEqualTo(200);
        times(punctual, at(day, 8, 0), at(day, 14, 0), at(day, 12, 0));
        // 23:30 in Bogotá is already the next day in UTC: it still belongs to the 15th.
        times(partial, at(day, 9, 0), at(day, 23, 59), at(day, 23, 30));
        times(overdue, at(day, 8, 0), at(day, 9, 0), null);
        // Dispatched inside the period, arrived after it: never counted for the dispatch date.
        times(nextDay, at(day, 20, 0), at(day.plusDays(1), 2, 0), at(day.plusDays(1), 0, 30));
        Snapshot before = snapshot();

        JsonNode report = report(bManager, day, day, null);
        assertThat(report.get("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(report.at("/branch/externalId").asText()).isEqualTo(b.toString());
        // RF-0043-CA-02: 1 / 2 = 50 %, the overdue transfer apart.
        assertThat(report.get("summary")).isEqualTo(metrics(2, 1, 0, 1, "50.00"));
        assertThat(overdueIds(report)).containsExactly(overdue);
        assertThat(report.at("/overdueInTransit/0/routeCode").asText()).isEqualTo("AB" + suffix);
        assertThat(report.get("branches").size()).isEqualTo(1);
        assertThat(report.at("/branches/0/asDestination")).isEqualTo(metrics(2, 1, 0, 1, "50.00"));
        assertThat(report.at("/branches/0/asOrigin")).isEqualTo(metrics(0, 0, 0, 0, null));
        assertThat(route(report, "AB").get("metrics")).isEqualTo(metrics(2, 1, 0, 1, "50.00"));
        assertThat(route(report, "CB").get("metrics")).isEqualTo(metrics(0, 0, 0, 0, null));

        // Resolving the discrepancy alert does not change 50 %.
        assertThat(bManager.post("/transfers/" + partial + "/discrepancy/resolve", Map.of("treatment", "RESEND")).statusCode()).isEqualTo(200);
        assertThat(report(bManager, day, day, null).get("summary")).isEqualTo(metrics(2, 1, 0, 1, "50.00"));
        // The next day holds only the arrival confirmed then.
        assertThat(report(bManager, day.plusDays(1), day.plusDays(1), null).get("summary")).isEqualTo(metrics(1, 1, 0, 0, "100.00"));
        // Without confirmed arrivals: «No calculable» (null), never an invented 0 %.
        JsonNode empty = report(bManager, day.minusDays(1), day.minusDays(1), null);
        assertThat(empty.get("summary")).isEqualTo(metrics(0, 0, 0, 0, null));
        assertThat(empty.get("overdueInTransit").size()).isZero();
        // Reading the report moves nothing.
        assertThat(movements()).isEqualTo(before.movements());
        assertThat(snapshot().transfers()).isEqualTo(before.transfers());
    }

    @Test void branchesAreReportedAsOriginAndDestinationApartAndRoutesByTheirOwnTransfers() throws Exception {
        LocalDate day = LocalDate.of(2025, 5, 20);
        String fromA = inTransit(a, "1");
        String fromC = inTransit(c, "1");
        assertThat(bOperator.post("/transfers/" + fromA + "/receive", quantities("1")).statusCode()).isEqualTo(200);
        assertThat(bOperator.post("/transfers/" + fromC + "/receive", quantities("1")).statusCode()).isEqualTo(200);
        times(fromA, at(day, 8, 0), at(day, 12, 0), at(day, 11, 0));
        // Complete but after the estimate: not compliant.
        times(fromC, at(day, 8, 0), at(day, 10, 0), at(day, 10, 1));

        // RF-0043-CA-01: the network report for the administrator.
        JsonNode network = report(admin, day, day, null);
        assertThat(network.get("branch").isNull()).isTrue();
        assertThat(network.get("summary")).isEqualTo(metrics(2, 1, 1, 0, "50.00"));
        assertThat(branchOf(network, a).get("asOrigin")).isEqualTo(metrics(1, 1, 0, 0, "100.00"));
        assertThat(branchOf(network, a).get("asDestination")).isEqualTo(metrics(0, 0, 0, 0, null));
        assertThat(branchOf(network, c).get("asOrigin")).isEqualTo(metrics(1, 0, 1, 0, "0.00"));
        assertThat(branchOf(network, b).get("asDestination")).isEqualTo(metrics(2, 1, 1, 0, "50.00"));
        assertThat(route(network, "AB").get("metrics")).isEqualTo(metrics(1, 1, 0, 0, "100.00"));
        assertThat(route(network, "AB").get("priority").asText()).isEqualTo("HIGH");
        assertThat(route(network, "CB").get("metrics")).isEqualTo(metrics(1, 0, 1, 0, "0.00"));

        // One branch: the administrator chooses it; its manager reads only its own.
        JsonNode onlyA = report(admin, day, day, a);
        assertThat(onlyA.get("summary")).isEqualTo(metrics(1, 1, 0, 0, "100.00"));
        assertThat(onlyA.get("branches").size()).isEqualTo(1);
        assertThat(onlyA.at("/routes/0/code").asText()).isEqualTo("AB" + suffix);
        assertThat(onlyA.get("routes").size()).isEqualTo(1);
        assertThat(report(aManager, day, day, a).get("summary")).isEqualTo(onlyA.get("summary"));
        assertThat(report(aManager, day, day, null).get("routes")).isEqualTo(onlyA.get("routes"));
        assertThat(report(cManager, day, day, null).get("summary")).isEqualTo(metrics(1, 0, 1, 0, "0.00"));
    }

    @Test void onlyManagersOfTheirBranchAndAdministratorsReadTheReport() throws Exception {
        String period = "from=2025-06-01&to=2025-06-30";
        assertError(aOperator.get("/logistics/compliance?" + period), 403, "ROLE_FORBIDDEN");
        assertError(bOperator.get("/logistics/compliance?" + period + "&branch=" + b), 403, "ROLE_FORBIDDEN");
        assertError(aManager.get("/logistics/compliance?" + period + "&branch=" + b), 403, "BRANCH_FORBIDDEN");
        assertError(admin.get("/logistics/compliance?" + period + "&branch=" + UUID.randomUUID()), 404, "BRANCH_NOT_FOUND");
        assertError(admin.get("/logistics/compliance?" + period + "&branch=00000000-0000-1000-8000-000000000000"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/logistics/compliance?to=2025-06-30"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/logistics/compliance?from=2025-06-01"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/logistics/compliance?from=2025-06-31&to=2025-07-01"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/logistics/compliance?from=2025-07-01&to=2025-06-30"), 400, "VALIDATION_FAILED");
        assertThat(new Browser().get("/logistics/compliance?" + period).statusCode()).isEqualTo(401);
        assertThat(admin.get("/logistics/compliance?from=2025-06-30&to=2025-06-30").statusCode()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- fixtures

    static OffsetDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atOffset(BOGOTA);
    }

    /** Moves the dispatch (and arrival, when given) of a transfer to fixed instants. */
    void times(String id, OffsetDateTime departed, OffsetDateTime estimated, OffsetDateTime arrived) {
        assertThat(jdbc.update("UPDATE logistics_dispatch SET dispatched_at=?,estimated_arrival_at=? WHERE transfer_id="
            + "(SELECT id FROM transfers_transfer WHERE external_id=?::uuid)", departed, estimated, id)).isEqualTo(1);
        if (arrived != null) {
            assertThat(jdbc.update("UPDATE logistics_arrival SET arrived_at=? WHERE dispatch_id=(SELECT d.id FROM logistics_dispatch d "
                + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid)", arrived, id)).isEqualTo(1);
        }
    }

    JsonNode report(Browser browser, LocalDate from, LocalDate to, UUID branch) throws Exception {
        HttpResponse<String> response = browser.get("/logistics/compliance?from=" + from + "&to=" + to
            + (branch == null ? "" : "&branch=" + branch));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return read(response);
    }

    JsonNode metrics(int arrivals, int onTime, int late, int discrepancy, String percent) {
        return json.readTree("{\"arrivals\":%d,\"onTimeComplete\":%d,\"lateComplete\":%d,\"discrepancy\":%d,\"compliancePercent\":%s}"
            .formatted(arrivals, onTime, late, discrepancy, percent == null ? "null" : "\"" + percent + "\""));
    }

    JsonNode branchOf(JsonNode report, UUID branch) {
        for (JsonNode item : report.get("branches")) if (item.at("/branch/externalId").asText().equals(branch.toString())) return item;
        throw new AssertionError("Branch not in report: " + branch);
    }

    JsonNode route(JsonNode report, String code) {
        for (JsonNode item : report.get("routes")) if (item.get("code").asText().equals(code + suffix)) return item;
        throw new AssertionError("Route not in report: " + code);
    }

    List<String> overdueIds(JsonNode report) {
        List<String> ids = new ArrayList<>();
        report.get("overdueInTransit").forEach(item -> ids.add(item.get("externalId").asText()));
        return ids;
    }

    Browser operatorOf(UUID origin) { return origin.equals(a) ? aOperator : cOperator; }

    Browser managerOf(UUID origin) { return origin.equals(a) ? aManager : cManager; }

    String requested(UUID origin, String quantity) throws Exception {
        HttpResponse<String> created = bOperator.post("/transfers", Map.of("origin", origin.toString(),
            "lines", List.of(Map.of("product", product.toString(), "unit", "unidad", "quantity", quantity))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    String prepared(String id, String quantity) throws Exception {
        UUID origin = UUID.fromString(read(bOperator.get("/transfers/" + id)).at("/origin/externalId").asText());
        HttpResponse<String> prepare = operatorOf(origin).post("/transfers/" + id + "/prepare", quantities(quantity));
        assertThat(prepare.statusCode()).as(prepare.body()).isEqualTo(200);
        return id;
    }

    /** Requested by b, prepared, approved and dispatched from {@code origin}, estimated to arrive in 4 hours. */
    String inTransit(UUID origin, String quantity) throws Exception {
        String id = prepared(requested(origin, quantity), quantity);
        assertThat(managerOf(origin).post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);
        OffsetDateTime now = OffsetDateTime.now(BOGOTA);
        HttpResponse<String> dispatched = operatorOf(origin).post("/transfers/" + id + "/dispatch", Map.of("carrier", "Transportes Andinos",
            "departedAt", now.toString(), "estimatedArrivalAt", now.plus(Duration.ofHours(4)).toString()));
        assertThat(dispatched.statusCode()).as(dispatched.body()).isEqualTo(200);
        return id;
    }

    static Map<String, Object> quantities(String quantity) {
        return Map.of("lines", List.of(Map.of("lineNo", 1, "quantity", quantity)));
    }

    void route(String code, UUID origin, UUID destination, String priority) throws Exception {
        assertThat(admin.post("/logistics/routes", Map.of("code", code + suffix, "origin", origin.toString(), "destination",
            destination.toString(), "priority", priority)).statusCode()).isEqualTo(201);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,'Maceta','unidad',false)",
            id, "SKU-" + UUID.randomUUID());
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        for (UUID branch : List.of(a, b, c)) {
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

    void stock(Browser operator, String quantity) throws Exception {
        HttpResponse<String> posted = operator.post("/inventory/movements", Map.of("kind", "MANUAL_ENTRY",
            "product", product.toString(), "unit", "unidad", "quantity", quantity, "reason", "Inventario inicial", "unitCost", "9"));
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(read(response).path("code").asText()).as(response.body()).isEqualTo(code);
    }

    List<String> ids(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        read(response).get("items").forEach(item -> ids.add(item.get("externalId").asText()));
        return ids;
    }

    int movements() { return jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class); }

    int arrivals() { return jdbc.queryForObject("SELECT count(*) FROM logistics_arrival", Integer.class); }

    record Snapshot(List<String> transfers, int movements, int arrivals, List<String> balances, List<String> transit) {}

    /** States, events, stock and transit of this test's branches: what a reading must never change. */
    Snapshot snapshot() {
        return new Snapshot(
            jdbc.queryForList("SELECT t.code||':'||t.status||':'||(SELECT count(*) FROM transfers_event e WHERE e.transfer_id=t.id) "
                + "FROM transfers_transfer t JOIN identity_branch d ON d.id=t.destination_branch_id WHERE d.external_id=? ORDER BY t.id",
                String.class, b),
            movements(), arrivals(),
            jdbc.queryForList("SELECT ib.branch_id||':'||ib.physical_qty||':'||ib.inventory_value FROM inventory_balance ib "
                + "WHERE ib.product_id=(SELECT id FROM catalog_product WHERE external_id=?) ORDER BY 1", String.class, product),
            jdbc.queryForList("SELECT it.pending_base_qty||':'||it.pending_base_value FROM inventory_transit it "
                + "JOIN logistics_dispatch_line dl ON dl.id=it.dispatch_line_id JOIN transfers_line l ON l.id=dl.transfer_line_id "
                + "JOIN transfers_transfer t ON t.id=l.transfer_id JOIN identity_branch d ON d.id=t.destination_branch_id "
                + "WHERE d.external_id=? ORDER BY 1", String.class, b));
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
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
