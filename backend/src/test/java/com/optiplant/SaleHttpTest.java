package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

/** RF-0027–RF-0029, RF-0031: sale confirmation through the inventory ledger, stock validation, discounts and records. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=sales-initial", "app.admin-password=private initial password"})
class SaleHttpTest {
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
    UUID a, b, product, other;
    String suffix, list;
    Browser admin, operator;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString();
        a = branch("a");
        b = branch("b");
        product = product("Maceta", a, b);
        other = product("Sustrato", a);
        admin = account("ADMIN", null);
        operator = account("OPERATOR", a);
        list = priceList("Detal");
        price(list, product, "unidad", "100");
        price(list, product, "caja", "1100");
        price(list, other, "unidad", "40");
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

    String priceList(String name) throws Exception {
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", "LP-" + UUID.randomUUID(), "name", name));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    void price(String priceList, UUID item, String unit, String value) throws Exception {
        HttpResponse<String> set = admin.post("/catalog/price-lists/" + priceList + "/prices",
            Map.of("product", item.toString(), "unit", unit, "price", value));
        assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
    }

    /** Valued entry of {@code quantity} base units at cost 5, posted by the branch operator through the ledger. */
    void stock(Browser who, UUID item, String quantity) throws Exception {
        Map<String, Object> entry = new HashMap<>(Map.of("kind", "MANUAL_ENTRY", "product", item.toString(), "unit", "unidad",
            "quantity", quantity, "reason", "Inventario inicial"));
        if (balance(a, item).getFirst().equals("0")) entry.put("unitCost", "5");
        HttpResponse<String> posted = who.post("/inventory/movements", entry);
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    static Map<String, Object> line(UUID item, String unit, String quantity, String discount) {
        return Map.of("product", item.toString(), "unit", unit, "quantity", quantity, "discountPercent", discount);
    }

    Map<String, Object> sale(List<?> lines) {
        return Map.of("priceList", list, "lines", lines);
    }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    static String code(HttpResponse<String> response) {
        return new JsonMapper().readTree(response.body()).path("code").asText();
    }

    /** physical quantity, value and average of a balance, as plain strings without trailing zeros. */
    List<String> balance(UUID branch, UUID item) {
        return jdbc.query("SELECT ib.physical_qty,ib.inventory_value,ib.average_base_cost FROM inventory_balance ib "
                + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
                + "WHERE b.external_id=? AND p.external_id=?",
            (r, i) -> List.of(plain(r.getBigDecimal(1)), plain(r.getBigDecimal(2)), plain(r.getBigDecimal(3))), branch, item)
            .stream().findFirst().orElse(List.of("0", "0", "0"));
    }

    static String plain(BigDecimal value) { return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString(); }

    /** Sales and sale withdrawals recorded in branch A; the database is shared by every test of the class. */
    List<Integer> effects() {
        return List.of(
            jdbc.queryForObject("SELECT count(*) FROM sales_sale s JOIN identity_branch b ON b.id=s.branch_id WHERE b.external_id=?",
                Integer.class, a),
            jdbc.queryForObject("SELECT count(*) FROM inventory_operation o JOIN identity_branch b ON b.id=("
                + "SELECT branch_id FROM sales_sale WHERE id=o.sale_id) WHERE o.kind='SALE' AND b.external_id=?", Integer.class, a));
    }

    @Test void confirmedSaleKeepsItsDataAndWithdrawsStockThroughTheLedger() throws Exception {
        stock(operator, product, "20");
        HttpResponse<String> confirmed = operator.post("/sales", sale(List.of(line(product, "unidad", "2", "10"),
            line(product, "caja", "1", "0"))));
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(201);
        JsonNode sale = read(confirmed);

        // RF-0027-CA-01: product, quantity, price, branch, date and responsible; stock decreases by the sold base units.
        assertThat(sale.get("code").asText()).startsWith("VT-");
        assertThat(sale.get("branch").get("externalId").asText()).isEqualTo(a.toString());
        assertThat(sale.get("confirmedBy").get("fullName").asText()).isEqualTo("Persona OPERATOR");
        assertThat(sale.get("confirmedAt").asText()).isNotBlank();
        JsonNode first = sale.get("lines").get(0);
        assertThat(first.get("productExternalId").asText()).isEqualTo(product.toString());
        assertThat(first.get("productName").asText()).isEqualTo("Maceta");
        assertThat(first.get("commercialQuantity").asText()).isEqualTo("2.000");
        assertThat(first.get("priceListName").asText()).isEqualTo("Detal");
        // RF-0029-CA-02: price 100 with 10% leaves a net unit price of 90; list price and discount stay identifiable.
        assertThat(first.get("unitPrice").asText()).isEqualTo("100");
        assertThat(first.get("discountPercent").asText()).isEqualTo("10");
        assertThat(first.get("netUnitPrice").asText()).isEqualTo("90");
        assertThat(first.get("netAmount").asText()).isEqualTo("180.00");
        JsonNode box = sale.get("lines").get(1);
        assertThat(box.get("baseQuantity").asText()).isEqualTo("12.000");
        assertThat(box.get("netAmount").asText()).isEqualTo("1100.00");
        assertThat(sale.get("grossTotal").asText()).isEqualTo("1300.00");
        assertThat(sale.get("discountTotal").asText()).isEqualTo("20.00");
        assertThat(sale.get("totalNet").asText()).isEqualTo("1280.00");
        assertThat(first.has("historicalBaseCost")).isFalse();
        assertThat(balance(a, product)).containsExactly("6", "30", "5");

        // The withdrawal is one auditable SALE operation of the ledger, one exit per line linked to its sale line.
        List<Map<String, Object>> exits = jdbc.queryForList("""
            SELECT m.direction,m.base_qty,m.external_id FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            WHERE o.external_id=?::uuid AND o.kind='SALE' AND m.sale_line_id IS NOT NULL ORDER BY m.id""",
            sale.get("operationExternalId").asText());
        assertThat(exits).hasSize(2);
        assertThat(exits).allSatisfy(exit -> assertThat(exit.get("direction")).isEqualTo("OUT"));
        assertThat(exits.get(0).get("external_id").toString()).isEqualTo(first.get("movementExternalId").asText());

        // RF-0031-CA-01: the record is consulted later with the same data.
        HttpResponse<String> detail = operator.get("/sales/" + sale.get("externalId").asText());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(read(detail)).isEqualTo(sale);

        // RF-0027-CA-02: a return references the sale exit and keeps the original sale unchanged.
        Map<String, Object> ret = Map.of("kind", "SALE_RETURN", "reference", first.get("movementExternalId").asText(),
            "quantity", "1", "reason", "Cliente devolvió");
        assertThat(operator.post("/inventory/movements", ret).statusCode()).isEqualTo(201);
        assertThat(read(operator.get("/sales/" + sale.get("externalId").asText()))).isEqualTo(sale);
        assertThat(balance(a, product).getFirst()).isEqualTo("7");
    }

    @Test void discountsBetweenZeroAndHundredApplyAndOthersAreRejectedWithoutEffects() throws Exception {
        stock(operator, product, "10");
        HttpResponse<String> zero = operator.post("/sales", sale(List.of(line(product, "unidad", "1", "0"))));
        assertThat(read(zero).get("lines").get(0).get("netUnitPrice").asText()).isEqualTo("100");
        HttpResponse<String> full = operator.post("/sales", sale(List.of(line(product, "unidad", "1", "100"))));
        assertThat(full.statusCode()).as(full.body()).isEqualTo(201);
        assertThat(read(full).get("lines").get(0).get("netUnitPrice").asText()).isEqualTo("0");
        assertThat(read(full).get("totalNet").asText()).isEqualTo("0.00");
        List<String> before = balance(a, product);
        List<Integer> effects = effects();

        for (String discount : List.of("100.5", "-1", "abc")) {
            HttpResponse<String> rejected = operator.post("/sales", sale(List.of(line(product, "unidad", "1", discount))));
            assertThat(rejected.statusCode()).as(discount).isEqualTo(400);
            assertThat(read(rejected).get("detail").asText()).contains("entre 0 y 100");
        }
        // No header discount and no free price outside the registered discount.
        HttpResponse<String> header = operator.post("/sales", Map.of("priceList", list, "discountPercent", "5",
            "lines", List.of(line(product, "unidad", "1", "0"))));
        assertThat(header.statusCode()).isEqualTo(400);
        Map<String, Object> priced = new HashMap<>(line(product, "unidad", "1", "0"));
        priced.put("unitPrice", "1");
        HttpResponse<String> free = operator.post("/sales", sale(List.of(priced)));
        assertThat(free.statusCode()).isEqualTo(400);
        assertThat(read(free).get("detail").asText()).contains("se toma de la lista");
        assertThat(balance(a, product)).isEqualTo(before);
        assertThat(effects()).isEqualTo(effects);
    }

    @Test void displayedTotalsStayConsistentWhenExactAmountsHaveMoreThanTwoDecimals() throws Exception {
        stock(operator, product, "5");
        price(list, product, "unidad", "1.005");
        JsonNode sale = read(operator.post("/sales", sale(List.of(line(product, "unidad", "1", "50")))));
        // Exact gross 1.005 and net 0.5025: the shown discount is the shown subtotal minus the shown net total.
        assertThat(sale.get("exactTotalNet").asText()).isEqualTo("0.5025");
        assertThat(sale.get("grossTotal").asText()).isEqualTo("1.01");
        assertThat(sale.get("totalNet").asText()).isEqualTo("0.50");
        assertThat(sale.get("discountTotal").asText()).isEqualTo("0.51");
    }

    @Test void insufficientStockRejectsTheWholeSaleWithoutRecordOrWithdrawal() throws Exception {
        stock(operator, product, "3");
        List<Integer> effects = effects();
        // RF-0028-CA-02: the second line has no stock, so the first one is not sold either.
        HttpResponse<String> missing = operator.post("/sales", sale(List.of(line(product, "unidad", "2", "0"),
            line(other, "unidad", "1", "0"))));
        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(code(missing)).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(read(missing).get("detail").asText()).contains("Sustrato").contains("No se confirmó la venta");
        // Lines of the same product are validated together, not one by one.
        HttpResponse<String> together = operator.post("/sales", sale(List.of(line(product, "unidad", "2", "0"),
            line(product, "unidad", "2", "0"))));
        assertThat(together.statusCode()).isEqualTo(409);
        assertThat(balance(a, product)).containsExactly("3", "15", "5");
        assertThat(effects()).isEqualTo(effects);

        // RF-0028-CA-01: sufficient stock lets the sale continue; selling everything leaves zero.
        assertThat(operator.post("/sales", sale(List.of(line(product, "unidad", "3", "0")))).statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("0", "0", "5");
    }

    @Test void repeatedConfirmationReturnsTheOriginalRecordWithoutASecondWithdrawal() throws Exception {
        stock(operator, product, "5");
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = sale(List.of(line(product, "unidad", "2", "0")));
        HttpResponse<String> first = operator.post("/sales", body, key);
        HttpResponse<String> again = operator.post("/sales", body, key);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(read(again)).isEqualTo(read(first));
        assertThat(effects()).containsExactly(1, 1);
        assertThat(balance(a, product).getFirst()).isEqualTo("3");

        HttpResponse<String> changed = operator.post("/sales", sale(List.of(line(product, "unidad", "1", "0"))), key);
        assertThat(changed.statusCode()).isEqualTo(409);
        assertThat(code(changed)).isEqualTo("IDEMPOTENCY_CONFLICT");
        HttpResponse<String> keyless = operator.post("/sales", body, null);
        assertThat(keyless.statusCode()).isEqualTo(400);
        assertThat(code(keyless)).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(effects()).containsExactly(1, 1);
    }

    @Test void concurrentConfirmationsNeverSellTheSameUnits() throws Exception {
        stock(operator, product, "3");
        Browser second = account("OPERATOR", a);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (Browser browser : List.of(operator, second)) {
            Callable<Integer> call = () -> { start.await();
                return browser.post("/sales", sale(List.of(line(product, "unidad", "2", "0")))).statusCode(); };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) statuses.add(result.get());
        pool.shutdown();
        // RF-0028-CA-03: only one confirms; 1 unit remains and the rejected one leaves no record or withdrawal.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(balance(a, product)).containsExactly("1", "5", "5");
        assertThat(effects()).containsExactly(1, 1);
    }

    @Test void recordKeepsItsCommercialSnapshotsAndCannotBeEdited() throws Exception {
        stock(operator, product, "5");
        JsonNode sale = read(operator.post("/sales", sale(List.of(line(product, "unidad", "2", "25")))));
        String id = sale.get("externalId").asText();

        // RF-0031-CA-02: later price and catalog changes do not alter the confirmed record or its total.
        price(list, product, "unidad", "150");
        String code = jdbc.queryForObject("SELECT code FROM catalog_price_list WHERE external_id=?::uuid", String.class, list);
        assertThat(admin.post("/catalog/price-lists/" + list + "/update", Map.of("code", code, "name", "Detal 2027", "active", true))
            .statusCode()).isEqualTo(200);
        jdbc.update("UPDATE catalog_product SET name='Maceta renombrada' WHERE external_id=?", product);
        JsonNode later = read(admin.get("/sales/" + id));
        assertThat(later).isEqualTo(sale);
        assertThat(later.get("lines").get(0).get("productName").asText()).isEqualTo("Maceta");
        assertThat(later.get("lines").get(0).get("priceListName").asText()).isEqualTo("Detal");
        assertThat(later.get("totalNet").asText()).isEqualTo("150.00");

        // The record is immutable for the application role and has no edit or delete route.
        assertThatThrownBy(() -> jdbc.update("UPDATE sales_line SET discount_pct=0 WHERE sale_id=(SELECT id FROM sales_sale "
            + "WHERE external_id=?::uuid)", id)).hasRootCauseMessage("ERROR: permission denied for table sales_line");
        assertThatThrownBy(() -> jdbc.update("UPDATE sales_sale SET confirmed_at=now() WHERE external_id=?::uuid", id))
            .hasRootCauseMessage("ERROR: permission denied for table sales_sale");
        assertThat(operator.delete("/sales/" + id).statusCode()).isEqualTo(405);
    }

    @Test void onlyTheBranchOperatorConfirmsWithAnActiveListAndOperableProducts() throws Exception {
        stock(operator, product, "5");
        Map<String, Object> body = sale(List.of(line(product, "unidad", "1", "0")));
        HttpResponse<String> manager = account("MANAGER", a).post("/sales", body);
        assertThat(manager.statusCode()).isEqualTo(403);
        assertThat(code(manager)).isEqualTo("SALE_FORBIDDEN");
        assertThat(admin.post("/sales", body).statusCode()).isEqualTo(403);

        // A product without a price in the chosen list, an inactive list or a product not enabled in the branch is rejected.
        String wholesale = priceList("Mayorista");
        HttpResponse<String> unpriced = operator.post("/sales", Map.of("priceList", wholesale,
            "lines", List.of(line(product, "unidad", "1", "0"))));
        assertThat(unpriced.statusCode()).isEqualTo(409);
        assertThat(code(unpriced)).isEqualTo("PRICE_NOT_DEFINED");
        assertThat(read(unpriced).get("detail").asText()).startsWith("Línea 1: ");
        String code = jdbc.queryForObject("SELECT code FROM catalog_price_list WHERE external_id=?::uuid", String.class, wholesale);
        admin.post("/catalog/price-lists/" + wholesale + "/update", Map.of("code", code, "name", "Mayorista", "active", false));
        assertThat(code(operator.post("/sales", Map.of("priceList", wholesale, "lines", List.of(line(product, "unidad", "1", "0"))))))
            .isEqualTo("PRICE_LIST_INACTIVE");
        UUID foreign = product("Ajeno", b);
        price(list, foreign, "unidad", "10");
        assertThat(code(operator.post("/sales", sale(List.of(line(foreign, "unidad", "1", "0")))))).isEqualTo("PRODUCT_NOT_OPERABLE");
        // Indivisible units admit whole quantities only.
        assertThat(operator.post("/sales", sale(List.of(line(product, "unidad", "1.5", "0")))).statusCode()).isEqualTo(400);
        assertThat(effects()).containsExactly(0, 0);

        // An inactive branch keeps its history but admits no new sale.
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        assertThat(code(operator.post("/sales", body))).isEqualTo("BRANCH_INACTIVE");
    }

    @Test void recordsAreReadWithinTheProfileScope() throws Exception {
        stock(operator, product, "5");
        String id = read(operator.post("/sales", sale(List.of(line(product, "unidad", "1", "0"))))).get("externalId").asText();

        HttpResponse<String> own = account("MANAGER", a).get("/sales");
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(read(own).get("branch").get("externalId").asText()).isEqualTo(a.toString());
        assertThat(read(own).get("total").asInt()).isEqualTo(1);
        assertThat(read(own).get("items").get(0).get("externalId").asText()).isEqualTo(id);

        HttpResponse<String> network = admin.get("/sales");
        assertThat(network.statusCode()).isEqualTo(200);
        assertThat(read(network).get("branch").isNull()).isTrue();
        assertThat(read(admin.get("/sales?branch=" + b)).get("total").asInt()).isZero();
        assertThat(admin.get("/sales/" + id).statusCode()).isEqualTo(200);

        Browser foreign = account("OPERATOR", b);
        assertThat(foreign.get("/sales/" + id).statusCode()).isEqualTo(403);
        assertThat(foreign.get("/sales?branch=" + a).statusCode()).isEqualTo(403);
        assertThat(read(foreign.get("/sales")).get("total").asInt()).isZero();
        assertThat(operator.get("/sales/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(operator.get("/sales?limit=0").statusCode()).isEqualTo(400);
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> delete(String path) throws Exception {
            JsonNode csrf = json.readTree(get("/auth/csrf").body());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).DELETE()
                .header("Idempotency-Key", UUID.randomUUID().toString());
            if (csrf.has("headerName")) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
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
