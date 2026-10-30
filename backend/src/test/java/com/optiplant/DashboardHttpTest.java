package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * RF-0044: sales of the current month and the three previous ones (America/Bogota), net of discounts, with returns apart
 * and the months before the branch existed without data. RF-0045: rotation = units sold / time-weighted average of the
 * physical quantity rebuilt from the ledger, «No calculable» without enough history or with a zero average. Sales and
 * ledger instants are immutable for the application: the fixtures move them with the database owner.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=dashboard-initial", "app.admin-password=private initial password"})
class DashboardHttpTest {
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
    UUID a, b, maceta, sustrato, abono, pala;
    String suffix, list;
    Browser admin, aOperator, aManager, bManager;

    @BeforeEach void data() throws Exception {
        owner = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        maceta = product("A-Maceta");
        sustrato = product("B-Sustrato");
        abono = product("C-Abono");
        pala = product("D-Pala");
        admin = account("ADMIN", null);
        aOperator = account("OPERATOR", a);
        aManager = account("MANAGER", a);
        bManager = account("MANAGER", b);
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", "LP-" + suffix, "name", "Detal"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        list = read(created).get("externalId").asText();
        for (UUID item : List.of(maceta, sustrato, abono, pala)) {
            assertThat(admin.post("/catalog/price-lists/" + list + "/prices", Map.of("product", item.toString(), "unit", "unidad",
                "price", "100")).statusCode()).isEqualTo(200);
        }
    }

    // ---------------------------------------------------------------- RF-0044

    @Test void theCurrentMonthIsComparedWithTheThreePreviousOnes() throws Exception {
        YearMonth current = YearMonth.now(BOGOTA);
        // The branch was registered on the 10th, three months ago: that month is only partly covered.
        owner.update("UPDATE identity_branch SET created_at=? WHERE external_id=?", noon(current.minusMonths(3).atDay(10)), a);
        stock(maceta, "100");
        stock(sustrato, "50");
        String oldest = sale(sustrato, "1", "60");
        String previous = sale(maceta, "2", "10");
        String now = sale(maceta, "1", "0");
        confirmedAt(oldest, noon(current.minusMonths(3).atDay(15)));
        confirmedAt(previous, noon(current.minusMonths(1).atDay(15)));
        // A return is shown apart and never subtracted from the sale.
        String sold = owner.queryForObject("SELECT m.external_id::text FROM inventory_movement m JOIN inventory_operation o ON "
            + "o.id=m.operation_id JOIN sales_sale s ON s.id=o.sale_id WHERE s.external_id=?::uuid", String.class, now);
        HttpResponse<String> returned = aOperator.post("/inventory/movements", Map.of("kind", "SALE_RETURN", "reference", sold,
            "quantity", "1", "reason", "Cliente devolvió"));
        assertThat(returned.statusCode()).as(returned.body()).isEqualTo(201);
        int movements = movements();

        JsonNode report = ok(aManager.get("/dashboard/sales-comparison"));
        // The server reads its own clock: the expectations follow the day it answered for, and a run that crossed into
        // another month after the fixtures were placed is skipped instead of failing on the calendar.
        LocalDate today = LocalDate.ofInstant(Instant.parse(report.get("generatedAt").asText()), BOGOTA);
        Assumptions.assumeTrue(YearMonth.from(today).equals(current), "The run crossed a month boundary in Bogotá");
        assertThat(report.get("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(report.at("/branch/externalId").asText()).isEqualTo(a.toString());
        JsonNode months = report.get("months");
        assertThat(months.size()).isEqualTo(4);
        // RF-0044-CA-01: each period apart, oldest first, matching the sales of the branch.
        JsonNode m3 = months.get(0);
        assertThat(m3.get("month").asText()).isEqualTo(current.minusMonths(3).toString());
        assertThat(m3.get("dataAvailable").asBoolean()).isTrue();
        assertThat(m3.get("partialHistory").asBoolean()).isTrue();
        assertThat(m3.get("inProgress").asBoolean()).isFalse();
        assertThat(m3.get("to").asText()).isEqualTo(current.minusMonths(3).atEndOfMonth().toString());
        assertThat(m3.get("salesCount").asInt()).isEqualTo(1);
        // 100 with 60 % of discount.
        assertThat(m3.get("netAmount").asText()).isEqualTo("40.00");
        assertThat(m3.at("/products/0/sku").asText()).startsWith("B-Sustrato");
        assertThat(m3.at("/products/0/soldQuantity").asText()).isEqualTo("1.000");
        // A month with the branch registered and no sales is a real zero, not missing data.
        JsonNode m2 = months.get(1);
        assertThat(m2.get("dataAvailable").asBoolean()).isTrue();
        assertThat(m2.get("partialHistory").asBoolean()).isFalse();
        assertThat(m2.get("salesCount").asInt()).isZero();
        assertThat(m2.get("netAmount").asText()).isEqualTo("0.00");
        assertThat(m2.get("products").size()).isZero();
        JsonNode m1 = months.get(2);
        assertThat(m1.get("salesCount").asInt()).isEqualTo(1);
        assertThat(m1.get("netAmount").asText()).isEqualTo("180.00");
        assertThat(m1.at("/products/0/soldQuantity").asText()).isEqualTo("2.000");
        assertThat(m1.get("returnsCount").asInt()).isZero();
        JsonNode m0 = months.get(3);
        assertThat(m0.get("month").asText()).isEqualTo(current.toString());
        assertThat(m0.get("inProgress").asBoolean()).isTrue();
        assertThat(m0.get("to").asText()).isEqualTo(today.toString());
        assertThat(m0.get("salesCount").asInt()).isEqualTo(1);
        assertThat(m0.get("netAmount").asText()).isEqualTo("100.00");
        assertThat(m0.get("returnsCount").asInt()).isEqualTo(1);
        assertThat(m0.at("/products/0/soldQuantity").asText()).isEqualTo("1.000");
        assertThat(m0.at("/products/0/returnedQuantity").asText()).isEqualTo("1.000");

        // The administrator and the operator of the branch read the same comparison; the other branch has no sales and,
        // registered today, no data for the previous months.
        assertThat(ok(admin.get("/dashboard/sales-comparison?branch=" + a)).get("months")).isEqualTo(months);
        assertThat(ok(aOperator.get("/dashboard/sales-comparison")).get("months")).isEqualTo(months);
        JsonNode other = ok(bManager.get("/dashboard/sales-comparison")).get("months");
        for (int i = 0; i < 3; i++) {
            assertThat(other.get(i).get("dataAvailable").asBoolean()).isFalse();
            assertThat(other.get(i).get("salesCount").isNull()).isTrue();
            assertThat(other.get(i).get("netAmount").isNull()).isTrue();
        }
        assertThat(other.get(3).get("salesCount").asInt()).isZero();
        // Registered during the month in progress: it does not cover the whole month.
        assertThat(other.get(3).get("partialHistory").asBoolean()).isTrue();
        // Reading moves nothing.
        assertThat(movements()).isEqualTo(movements);
    }

    @Test void theDashboardIsLimitedToTheOwnBranchExceptForTheAdministrator() throws Exception {
        assertError(aManager.get("/dashboard/sales-comparison?branch=" + b), 403, "BRANCH_FORBIDDEN");
        assertError(aOperator.get("/dashboard/rotation?from=2025-03-01&to=2025-03-10&branch=" + b), 403, "BRANCH_FORBIDDEN");
        assertError(admin.get("/dashboard/sales-comparison"), 400, "VALIDATION_FAILED");
        assertError(admin.get("/dashboard/sales-comparison?branch=" + UUID.randomUUID()), 404, "BRANCH_NOT_FOUND");
        assertError(admin.get("/dashboard/sales-comparison?branch=00000000-0000-1000-8000-000000000000"), 400, "VALIDATION_FAILED");
        assertThat(admin.get("/dashboard/sales-comparison?branch=" + b).statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/dashboard/sales-comparison").statusCode()).isEqualTo(401);
        assertThat(new Browser().get("/dashboard/rotation?from=2025-03-01&to=2025-03-10").statusCode()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- RF-0045

    @Test void rotationDividesTheUnitsSoldByTheTimeWeightedAverageOfThePhysicalQuantity() throws Exception {
        LocalDate first = LocalDate.of(2025, 3, 1);
        // maceta: 10 since February; on the 6th 20 enter at 00:00 and 20 are sold at 12:00, so during half a day there are 30:
        // (10·5 + 30·0.5 + 10·4.5) / 10 days = 11.
        stock(maceta, "10");
        stock(maceta, "20");
        String sold = sale(maceta, "20", "0");
        // sustrato: its ledger starts on the 3rd, inside the period.
        stock(sustrato, "5");
        // abono: entered and taken out before the period: a zero average.
        stock(abono, "1");
        HttpResponse<String> exit = aOperator.post("/inventory/movements", Map.of("kind", "MANUAL_EXIT", "product", abono.toString(),
            "unit", "unidad", "quantity", "1", "reason", "Merma"));
        assertThat(exit.statusCode()).as(exit.body()).isEqualTo(201);
        // pala: its ledger starts today, after the period: not listed.
        stock(pala, "3");
        List<Long> macetaOps = operations(maceta);
        occurredAt(macetaOps.get(0), midnight(first.minusDays(28)));
        occurredAt(macetaOps.get(1), midnight(first.plusDays(5)));
        occurredAt(macetaOps.get(2), noon(first.plusDays(5)));
        confirmedAt(sold, noon(first.plusDays(5)));
        historyStarted(maceta, midnight(first.minusDays(28)));
        occurredAt(operations(sustrato).getFirst(), midnight(first.plusDays(2)));
        historyStarted(sustrato, midnight(first.plusDays(2)));
        for (Long op : operations(abono)) occurredAt(op, midnight(first.minusDays(20)));
        historyStarted(abono, midnight(first.minusDays(20)));
        int movements = movements();

        JsonNode report = ok(aManager.get("/dashboard/rotation?from=2025-03-01&to=2025-03-10"));
        assertThat(report.get("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(report.get("measuredUntil").asText()).isEqualTo("2025-03-11T05:00:00Z");
        JsonNode items = report.get("items");
        assertThat(items.size()).isEqualTo(3);
        // RF-0045-CA-01: 20 sold over the time-weighted average of 11 (20 / 11 = 1.818…).
        assertThat(item(items, maceta)).isEqualTo(expected(maceta, "20.000", "11.000", "1.82", "CALCULATED"));
        // RF-0045-CA-02: before the 6th the quantity is a constant 10; the later changes are undone, not averaged.
        JsonNode before = ok(aManager.get("/dashboard/rotation?from=2025-03-01&to=2025-03-05")).get("items");
        assertThat(item(before, maceta)).isEqualTo(expected(maceta, "0.000", "10.000", "0.00", "CALCULATED"));
        // RF-0045-CA-03: incomplete history is «No calculable», never completed with zeros.
        assertThat(item(items, sustrato)).isEqualTo(expected(sustrato, "0.000", null, null, "INSUFFICIENT_HISTORY"));
        assertThat(item(items, abono)).isEqualTo(expected(abono, "0.000", "0.000", null, "ZERO_AVERAGE"));
        // Enough history, a positive average and no sales: a real zero.
        JsonNode quiet = ok(aManager.get("/dashboard/rotation?from=2025-03-07&to=2025-03-10")).get("items");
        assertThat(item(quiet, maceta)).isEqualTo(expected(maceta, "0.000", "10.000", "0.00", "CALCULATED"));
        // The administrator reads the same branch; another branch has no ledger for these products.
        assertThat(ok(admin.get("/dashboard/rotation?from=2025-03-01&to=2025-03-10&branch=" + a)).get("items")).isEqualTo(items);
        assertThat(ok(bManager.get("/dashboard/rotation?from=2025-03-01&to=2025-03-10")).get("items").size()).isZero();
        assertThat(movements()).isEqualTo(movements);
    }

    @Test void theRotationPeriodIsValidated() throws Exception {
        assertError(aManager.get("/dashboard/rotation?to=2025-03-10"), 400, "VALIDATION_FAILED");
        assertError(aManager.get("/dashboard/rotation?from=2025-03-10&to=2025-03-01"), 400, "VALIDATION_FAILED");
        assertError(aManager.get("/dashboard/rotation?from=2025-03-01&to=2025-02-30"), 400, "VALIDATION_FAILED");
        assertError(aManager.get("/dashboard/rotation?from=0001-01-01&to=2025-03-01"), 400, "VALIDATION_FAILED");
        // Today as the server sees it, not as this machine's clock does.
        LocalDate today = LocalDate.ofInstant(Instant.parse(ok(aManager.get("/dashboard/sales-comparison")).get("generatedAt").asText()),
            BOGOTA);
        LocalDate tomorrow = today.plusDays(1);
        assertError(aManager.get("/dashboard/rotation?from=2025-03-01&to=" + tomorrow), 400, "VALIDATION_FAILED");
        // Today is measured until now.
        assertThat(aManager.get("/dashboard/rotation?from=" + today + "&to=" + today).statusCode()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- fixtures

    static OffsetDateTime noon(LocalDate day) { return day.atTime(12, 0).atZone(BOGOTA).toOffsetDateTime(); }

    static OffsetDateTime midnight(LocalDate day) { return day.atStartOfDay(BOGOTA).toOffsetDateTime(); }

    JsonNode expected(UUID product, String sold, String average, String rotation, String status) {
        Map<String, Object> node = new HashMap<>();
        node.put("soldQuantity", sold);
        node.put("averageQuantity", average);
        node.put("rotation", rotation);
        node.put("status", status);
        node.put("productExternalId", product.toString());
        return json.valueToTree(node);
    }

    /** The compared fields of the item of {@code product}. */
    JsonNode item(JsonNode items, UUID product) {
        for (JsonNode item : items) {
            if (item.get("productExternalId").asText().equals(product.toString())) {
                Map<String, Object> node = new HashMap<>();
                for (String field : List.of("soldQuantity", "averageQuantity", "rotation", "status", "productExternalId")) {
                    node.put(field, item.get(field).isNull() ? null : item.get(field).asText());
                }
                return json.valueToTree(node);
            }
        }
        throw new AssertionError("Product not listed: " + product);
    }

    /** Ids of the ledger operations of {@code product} in branch a, in posting order. */
    List<Long> operations(UUID product) {
        return owner.queryForList("SELECT DISTINCT o.id FROM inventory_operation o JOIN inventory_movement m ON m.operation_id=o.id "
            + "JOIN catalog_product p ON p.id=m.product_id JOIN identity_branch b ON b.id=m.branch_id "
            + "WHERE p.external_id=? AND b.external_id=? ORDER BY o.id", Long.class, product, a);
    }

    void occurredAt(long operation, OffsetDateTime at) {
        assertThat(owner.update("UPDATE inventory_operation SET occurred_at=? WHERE id=?", at, operation)).isEqualTo(1);
    }

    void historyStarted(UUID product, OffsetDateTime at) {
        assertThat(owner.update("UPDATE inventory_balance SET history_started_at=? WHERE product_id=(SELECT id FROM catalog_product "
            + "WHERE external_id=?) AND branch_id=(SELECT id FROM identity_branch WHERE external_id=?)", at, product, a)).isEqualTo(1);
    }

    void confirmedAt(String sale, OffsetDateTime at) {
        assertThat(owner.update("UPDATE sales_sale SET confirmed_at=? WHERE external_id=?::uuid", at, sale)).isEqualTo(1);
    }

    String sale(UUID product, String quantity, String discount) throws Exception {
        HttpResponse<String> confirmed = aOperator.post("/sales", Map.of("priceList", list, "lines", List.of(Map.of(
            "product", product.toString(), "unit", "unidad", "quantity", quantity, "discountPercent", discount))));
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(201);
        return read(confirmed).get("externalId").asText();
    }

    /** A manual entry; the first one of the product is valued at 9, later ones at the current average. */
    void stock(UUID product, String quantity) throws Exception {
        Map<String, Object> entry = new HashMap<>(Map.of("kind", "MANUAL_ENTRY", "product", product.toString(), "unit", "unidad",
            "quantity", quantity, "reason", "Inventario inicial"));
        if (operations(product).isEmpty()) entry.put("unitCost", "9");
        HttpResponse<String> posted = aOperator.post("/inventory/movements", entry);
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    UUID product(String sku) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,'unidad',false)",
            id, sku + "-" + UUID.randomUUID(), sku.substring(2));
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty) SELECT b.id,p.id,true,0 "
            + "FROM identity_branch b CROSS JOIN catalog_product p WHERE b.external_id=? AND p.external_id=?", a, id);
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
