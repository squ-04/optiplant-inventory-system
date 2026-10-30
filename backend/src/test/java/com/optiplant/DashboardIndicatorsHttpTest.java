package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-0046 demand by units sold in a month, RF-0047 active transfers with their effect on inventory, RF-0048 products about
 * to run out with the only restock condition, and RF-0049 the network comparison, exclusive to the general administrator.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=indicators-initial", "app.admin-password=private initial password"})
class DashboardIndicatorsHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "optiplant_test_runtime");
        r.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    JdbcTemplate owner;
    UUID a, b, maceta, sustrato, abono, pala, tierra;
    String suffix, list;
    Browser admin, aOperator, aManager, bOperator, bManager;

    @BeforeEach void data() throws Exception {
        owner = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        maceta = product("A-Maceta", "unidad");
        sustrato = product("B-Sustrato", "unidad");
        abono = product("C-Abono", "unidad");
        pala = product("D-Pala", "unidad");
        tierra = product("E-Tierra", "kg");
        admin = account("ADMIN", null);
        aOperator = account("OPERATOR", a);
        aManager = account("MANAGER", a);
        bOperator = account("OPERATOR", b);
        bManager = account("MANAGER", b);
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", "LP-" + suffix, "name", "Detal"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        list = read(created).get("externalId").asText();
        for (UUID item : List.of(maceta, sustrato, abono, pala)) price(item, "unidad");
        price(tierra, "kg");
        assertThat(admin.post("/logistics/routes", Map.of("code", "AB" + suffix, "origin", a.toString(),
            "destination", b.toString(), "priority", "HIGH")).statusCode()).isEqualTo(201);
    }

    // ---------------------------------------------------------------- RF-0046

    @Test void demandRanksTheUnitsSoldInTheConsultedMonth() throws Exception {
        YearMonth current = YearMonth.now(BOGOTA);
        YearMonth previous = current.minusMonths(1);
        owner.update("UPDATE identity_branch SET created_at=? WHERE external_id=?", noon(current.minusMonths(3).atDay(1)), a);
        for (UUID item : List.of(maceta, sustrato, abono, pala)) stock(item, "unidad", "100");
        stock(tierra, "kg", "100");
        sale(maceta, "unidad", "30", "0");
        sale(sustrato, "unidad", "20", "90");
        sale(abono, "unidad", "10", "0");
        sale(tierra, "kg", "5", "0");
        confirmedAt(sale(pala, "unidad", "50", "0"), noon(previous.atDay(15)));
        int movements = movements();

        JsonNode report = ok(aManager.get("/dashboard/demand"));
        LocalDate today = serverDay(report);
        Assumptions.assumeTrue(YearMonth.from(today).equals(current), "The run crossed a month boundary in Bogotá");
        assertThat(report.get("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(report.get("month").asText()).isEqualTo(current.toString());
        assertThat(report.get("inProgress").asBoolean()).isTrue();
        assertThat(report.get("to").asText()).isEqualTo(today.toString());
        assertThat(report.get("dataAvailable").asBoolean()).isTrue();
        // RF-0046-CA-02: the price plays no part (sustrato has a 90 % discount); pala sold in another month.
        JsonNode rankings = report.get("rankings");
        assertThat(rankings.size()).isEqualTo(2);
        JsonNode kilos = rankings.get(0);
        assertThat(kilos.get("baseUnitName").asText()).isEqualTo("kg");
        assertThat(names(kilos.get("high"))).containsExactly("Tierra");
        assertThat(kilos.get("low").size()).isZero();
        JsonNode units = rankings.get(1);
        assertThat(units.get("productsWithSales").asInt()).isEqualTo(3);
        assertThat(names(units.get("high"))).containsExactly("Maceta", "Sustrato");
        assertThat(units.at("/high/0/soldQuantity").asText()).isEqualTo("30.000");
        assertThat(names(units.get("low"))).containsExactly("Abono");
        assertThat(names(report.get("withoutSales"))).containsExactly("Pala");

        // Another month has its own ranking, and it is a closed month.
        JsonNode other = ok(aManager.get("/dashboard/demand?month=" + previous));
        assertThat(other.get("inProgress").asBoolean()).isFalse();
        assertThat(other.get("to").asText()).isEqualTo(previous.atEndOfMonth().toString());
        assertThat(names(other.at("/rankings/0/high"))).containsExactly("Pala");
        assertThat(names(other.get("withoutSales"))).containsExactly("Maceta", "Sustrato", "Abono", "Tierra");

        // A month without sales: no group is invented and the products go apart.
        JsonNode quiet = ok(aManager.get("/dashboard/demand?month=" + current.minusMonths(2)));
        assertThat(quiet.get("dataAvailable").asBoolean()).isTrue();
        assertThat(quiet.get("rankings").size()).isZero();
        assertThat(quiet.get("withoutSales").size()).isEqualTo(5);

        // A month that ended before the branch existed has no data, which is not the same as no sales.
        JsonNode before = ok(aManager.get("/dashboard/demand?month=" + current.minusMonths(4)));
        assertThat(before.get("dataAvailable").asBoolean()).isFalse();
        assertThat(before.get("withoutSales").size()).isZero();

        assertThat(ok(admin.get("/dashboard/demand?branch=" + a)).get("rankings")).isEqualTo(rankings);
        assertThat(ok(aOperator.get("/dashboard/demand")).get("rankings")).isEqualTo(rankings);
        assertThat(movements()).isEqualTo(movements);
    }

    @Test void theMonthIsValidatedAndTheBranchKeepsItsScope() throws Exception {
        LocalDate today = serverDay(ok(aManager.get("/dashboard/demand")));
        assertError(aManager.get("/dashboard/demand?month=2025-13"), 400, "VALIDATION_FAILED");
        assertError(aManager.get("/dashboard/demand?month=marzo"), 400, "VALIDATION_FAILED");
        assertError(aManager.get("/dashboard/demand?month=" + YearMonth.from(today).plusMonths(1)), 400, "VALIDATION_FAILED");
        assertError(admin.get("/dashboard/demand"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/dashboard/transfers?branch=" + UUID.randomUUID()), 404, "BRANCH_NOT_FOUND");
        for (String path : List.of("demand", "transfers", "restock")) {
            assertError(aManager.get("/dashboard/" + path + "?branch=" + b), 403, "BRANCH_FORBIDDEN");
            assertError(aOperator.get("/dashboard/" + path + "?branch=" + b), 403, "BRANCH_FORBIDDEN");
            assertThat(new Browser().get("/dashboard/" + path).statusCode()).isEqualTo(401);
        }
    }

    // ---------------------------------------------------------------- RF-0047

    @Test void activeTransfersShowTheirEffectOnEachBranch() throws Exception {
        stock(maceta, "unidad", "100");
        String requested = requested("4");
        String approved = prepared(requested("6"), "5");
        assertThat(aManager.post("/transfers/" + approved + "/approve", Map.of()).statusCode()).isEqualTo(200);
        String moving = inTransit("10");
        String delivered = inTransit("2");
        assertThat(bOperator.post("/transfers/" + delivered + "/receive", quantities("2")).statusCode()).isEqualTo(200);
        String shortage = inTransit("10");
        assertThat(bOperator.post("/transfers/" + shortage + "/receive", quantities("7")).statusCode()).isEqualTo(200);
        String settled = inTransit("10");
        assertThat(bOperator.post("/transfers/" + settled + "/receive", quantities("8")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + settled + "/discrepancy/resolve", Map.of("treatment", "ADJUSTMENT")).statusCode())
            .isEqualTo(200);
        HttpResponse<String> lost = aManager.post("/transfers/" + settled + "/discrepancy/transit-loss",
            Map.of("reason", "Mercancía perdida", "evidence", "Acta 15"));
        assertThat(lost.statusCode()).as(lost.body()).isEqualTo(200);

        JsonNode origin = ok(aManager.get("/dashboard/transfers"));
        JsonNode items = origin.get("items");
        // A complete reception is closed; the resolved alert with its transit written off is closed too.
        assertThat(ids(items)).containsExactlyInAnyOrder(requested, approved, moving, shortage);

        JsonNode forecast = item(items, requested);
        assertThat(forecast.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(forecast.get("role").asText()).isEqualTo("ORIGIN");
        assertThat(forecast.at("/counterpart/externalId").asText()).isEqualTo(b.toString());
        assertLine(forecast, "4.000", null, null, null, null);
        // The prepared quantity replaces the requested one in the forecast.
        assertLine(item(items, approved), "5.000", null, null, null, null);
        assertLine(item(items, moving), null, "10.000", null, null, null);
        assertThat(item(items, moving).get("estimatedArrivalAt").isNull()).isFalse();
        JsonNode pending = item(items, shortage);
        assertThat(pending.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(pending.get("alertStatus").asText()).isEqualTo("OPEN");
        assertLine(pending, null, null, "7.000", "3.000", "0.000");

        // The same transfers read from the destination, as the destination.
        JsonNode destination = ok(bManager.get("/dashboard/transfers")).get("items");
        assertThat(ids(destination)).containsExactlyInAnyOrder(requested, approved, moving, shortage);
        assertThat(item(destination, moving).get("role").asText()).isEqualTo("DESTINATION");

        // Resolving keeps it active while something is pending: the alert does not move a quantity.
        assertThat(aManager.post("/transfers/" + shortage + "/discrepancy/resolve", Map.of("treatment", "RESEND")).statusCode())
            .isEqualTo(200);
        JsonNode resolved = item(ok(aManager.get("/dashboard/transfers")).get("items"), shortage);
        assertThat(resolved.get("alertStatus").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("treatment").asText()).isEqualTo("RESEND");
        assertLine(resolved, null, null, "7.000", "3.000", "0.000");

        // An open alert with the loss already written off stays active, with the loss apart from the reception.
        String written = inTransit("10");
        assertThat(bOperator.post("/transfers/" + written + "/receive", quantities("6")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + written + "/discrepancy/transit-loss",
            Map.of("reason", "Mercancía perdida", "evidence", "Acta 16")).statusCode()).isEqualTo(200);
        assertLine(item(ok(aManager.get("/dashboard/transfers")).get("items"), written), null, null, "6.000", "0.000", "4.000");

        int movements = movements();
        assertThat(ok(admin.get("/dashboard/transfers?branch=" + a)).get("items").size()).isEqualTo(5);
        assertThat(movements()).isEqualTo(movements);
    }

    // ---------------------------------------------------------------- RF-0048

    @Test void aboutToRunOutUsesTheOnlyRestockCondition() throws Exception {
        minimum(maceta, "5");
        stock(maceta, "unidad", "5");
        minimum(sustrato, "5");
        stock(sustrato, "unidad", "6");
        // abono and pala have a zero minimum: only the one with no stock is listed.
        stock(pala, "unidad", "1");
        stock(tierra, "kg", "9");

        JsonNode report = ok(aManager.get("/dashboard/restock"));
        assertThat(report.at("/branch/externalId").asText()).isEqualTo(a.toString());
        assertThat(names(report.get("items"))).containsExactly("Maceta", "Abono");
        assertThat(report.at("/items/0/availableQuantity").asText()).isEqualTo("5.000");
        assertThat(report.at("/items/0/minimumQuantity").asText()).isEqualTo("5.000");
        assertThat(report.at("/items/0/baseUnitName").asText()).isEqualTo("unidad");

        // RF-0048-CA-02: the changed minimum is the only parameter.
        minimum(sustrato, "6");
        minimum(maceta, "4");
        assertThat(names(ok(aManager.get("/dashboard/restock")).get("items"))).containsExactly("Sustrato", "Abono");
        assertThat(ok(aOperator.get("/dashboard/restock")).get("items").size()).isEqualTo(2);
        // Each branch reads its own combinations: b only has maceta enabled, at zero with a zero minimum.
        assertThat(names(ok(bManager.get("/dashboard/restock")).get("items"))).containsExactly("Maceta");
    }

    // ---------------------------------------------------------------- RF-0049

    @Test void onlyTheAdministratorComparesTheBranches() throws Exception {
        assertError(aManager.get("/dashboard/branch-comparison"), 403, "ROLE_FORBIDDEN");
        assertError(aOperator.get("/dashboard/branch-comparison"), 403, "ROLE_FORBIDDEN");
        assertThat(new Browser().get("/dashboard/branch-comparison").statusCode()).isEqualTo(401);
        // RF-0049-CA-02: the manager keeps the dashboard of the own branch.
        assertThat(aManager.get("/dashboard/demand").statusCode()).isEqualTo(200);
        assertError(admin.get("/dashboard/branch-comparison?month=2025-00"), 400, "VALIDATION_FAILED");
    }

    @Test void theComparisonDistinguishesMissingDataZeroAndNotCalculable() throws Exception {
        YearMonth current = YearMonth.now(BOGOTA);
        YearMonth previous = current.minusMonths(1);
        owner.update("UPDATE identity_branch SET created_at=? WHERE external_id=?", noon(previous.atDay(1)), a);
        owner.update("UPDATE identity_branch SET created_at=? WHERE external_id=?", noon(current.atDay(1)), b);
        stock(maceta, "unidad", "100");
        sale(maceta, "unidad", "2", "10");
        confirmedAt(sale(maceta, "unidad", "1", "0"), noon(previous.atDay(15)));
        String arrived = inTransit("2");
        assertThat(bOperator.post("/transfers/" + arrived + "/receive", quantities("2")).statusCode()).isEqualTo(200);
        int movements = movements();

        JsonNode report = ok(admin.get("/dashboard/branch-comparison"));
        Assumptions.assumeTrue(YearMonth.from(serverDay(report)).equals(current), "The run crossed a month boundary in Bogotá");
        assertThat(report.get("month").asText()).isEqualTo(current.toString());
        assertThat(report.get("inProgress").asBoolean()).isTrue();
        JsonNode first = branchOf(report, a);
        assertThat(first.get("dataAvailable").asBoolean()).isTrue();
        assertThat(first.get("partialHistory").asBoolean()).isFalse();
        assertThat(first.get("salesCount").asInt()).isEqualTo(1);
        assertThat(first.get("netAmount").asText()).isEqualTo("180.00");
        // Same figures as the RF-0043 report: the transfer was delivered complete on time, seen from both ends.
        assertThat(first.at("/asOrigin/arrivals").asInt()).isEqualTo(1);
        assertThat(first.at("/asOrigin/compliancePercent").asText()).isEqualTo("100.00");
        assertThat(first.at("/asDestination/arrivals").asInt()).isZero();
        assertThat(first.at("/asDestination/compliancePercent").isNull()).isTrue();
        JsonNode second = branchOf(report, b);
        assertThat(second.get("partialHistory").asBoolean()).isTrue();
        assertThat(second.get("salesCount").asInt()).isZero();
        assertThat(second.get("netAmount").asText()).isEqualTo("0.00");
        assertThat(second.at("/asDestination/compliancePercent").asText()).isEqualTo("100.00");

        // In the previous month b did not exist: no data, not zero; a has a sale and nothing to calculate its compliance.
        JsonNode past = ok(admin.get("/dashboard/branch-comparison?month=" + previous));
        assertThat(past.get("inProgress").asBoolean()).isFalse();
        JsonNode missing = branchOf(past, b);
        assertThat(missing.get("dataAvailable").asBoolean()).isFalse();
        assertThat(missing.get("salesCount").isNull()).isTrue();
        assertThat(missing.get("netAmount").isNull()).isTrue();
        assertThat(missing.get("asOrigin").isNull()).isTrue();
        JsonNode earlier = branchOf(past, a);
        assertThat(earlier.get("partialHistory").asBoolean()).isTrue();
        assertThat(earlier.get("netAmount").asText()).isEqualTo("100.00");
        assertThat(earlier.at("/asOrigin/compliancePercent").isNull()).isTrue();
        assertThat(movements()).isEqualTo(movements);
    }

    // ---------------------------------------------------------------- fixtures

    static OffsetDateTime noon(LocalDate day) { return day.atTime(12, 0).atZone(BOGOTA).toOffsetDateTime(); }

    static LocalDate serverDay(JsonNode report) {
        return LocalDate.ofInstant(Instant.parse(report.get("generatedAt").asText()), BOGOTA);
    }

    void assertLine(JsonNode item, String forecast, String transit, String received, String shortage, String lost) {
        JsonNode line = item.at("/lines/0");
        Map<String, String> found = new HashMap<>();
        for (String field : List.of("forecastQuantity", "inTransitQuantity", "receivedQuantity", "pendingShortageQuantity", "lostQuantity")) {
            found.put(field, line.get(field).isNull() ? null : line.get(field).asText());
        }
        Map<String, String> expected = new HashMap<>();
        expected.put("forecastQuantity", forecast);
        expected.put("inTransitQuantity", transit);
        expected.put("receivedQuantity", received);
        expected.put("pendingShortageQuantity", shortage);
        expected.put("lostQuantity", lost);
        assertThat(found).isEqualTo(expected);
    }

    JsonNode branchOf(JsonNode report, UUID branch) {
        for (JsonNode item : report.get("branches")) {
            if (item.at("/branch/externalId").asText().equals(branch.toString())) return item;
        }
        throw new AssertionError("Branch not listed: " + branch);
    }

    JsonNode item(JsonNode items, String id) {
        for (JsonNode item : items) if (item.get("externalId").asText().equals(id)) return item;
        throw new AssertionError("Transfer not listed: " + id);
    }

    static List<String> ids(JsonNode items) {
        List<String> found = new ArrayList<>();
        items.forEach(item -> found.add(item.get("externalId").asText()));
        return found;
    }

    static List<String> names(JsonNode items) {
        List<String> found = new ArrayList<>();
        items.forEach(item -> found.add(item.get("name").asText()));
        return found;
    }

    String requested(String quantity) throws Exception {
        HttpResponse<String> created = bOperator.post("/transfers", Map.of("origin", a.toString(),
            "lines", List.of(Map.of("product", maceta.toString(), "unit", "unidad", "quantity", quantity))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    String prepared(String id, String quantity) throws Exception {
        HttpResponse<String> prepare = aOperator.post("/transfers/" + id + "/prepare", quantities(quantity));
        assertThat(prepare.statusCode()).as(prepare.body()).isEqualTo(200);
        return id;
    }

    String inTransit(String quantity) throws Exception {
        String id = prepared(requested(quantity), quantity);
        assertThat(aManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);
        OffsetDateTime now = OffsetDateTime.now(BOGOTA);
        HttpResponse<String> dispatched = aOperator.post("/transfers/" + id + "/dispatch", Map.of("carrier", "Transportes Andinos",
            "departedAt", now.toString(), "estimatedArrivalAt", now.plus(Duration.ofHours(4)).toString()));
        assertThat(dispatched.statusCode()).as(dispatched.body()).isEqualTo(200);
        return id;
    }

    static Map<String, Object> quantities(String quantity) {
        return Map.of("lines", List.of(Map.of("lineNo", 1, "quantity", quantity)));
    }

    void price(UUID product, String unit) throws Exception {
        assertThat(admin.post("/catalog/price-lists/" + list + "/prices", Map.of("product", product.toString(), "unit", unit,
            "price", "100")).statusCode()).isEqualTo(200);
    }

    void minimum(UUID product, String quantity) throws Exception {
        HttpResponse<String> changed = admin.post("/products/" + product + "/branches/" + a + "/minimum",
            Map.of("minimumQuantity", quantity));
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
    }

    void confirmedAt(String sale, OffsetDateTime at) {
        assertThat(owner.update("UPDATE sales_sale SET confirmed_at=? WHERE external_id=?::uuid", at, sale)).isEqualTo(1);
    }

    String sale(UUID product, String unit, String quantity, String discount) throws Exception {
        HttpResponse<String> confirmed = aOperator.post("/sales", Map.of("priceList", list, "lines", List.of(Map.of(
            "product", product.toString(), "unit", unit, "quantity", quantity, "discountPercent", discount))));
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(201);
        return read(confirmed).get("externalId").asText();
    }

    void stock(UUID product, String unit, String quantity) throws Exception {
        HttpResponse<String> posted = aOperator.post("/inventory/movements", Map.of("kind", "MANUAL_ENTRY", "product", product.toString(),
            "unit", unit, "quantity", quantity, "reason", "Inventario inicial", "unitCost", "9"));
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    UUID product(String sku, String unit) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,?,false)",
            id, sku + "-" + UUID.randomUUID(), sku.substring(2), unit);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,?,1,false,true FROM catalog_product WHERE external_id=?", unit, id);
        // Only the first product also operates in b, the destination of the transfers.
        for (UUID branch : "A-Maceta".equals(sku) ? List.of(a, b) : List.of(a)) {
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

    int movements() { return jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class); }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    JsonNode ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return read(response);
    }

    void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(read(response).path("code").asText()).as(response.body()).isEqualTo(code);
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
