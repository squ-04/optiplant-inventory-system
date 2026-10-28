package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

/** RF-0021, RF-0023–RF-0025: suppliers, purchase orders, complete receipt through the inventory ledger and history. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=purchases-initial", "app.admin-password=private initial password"})
class PurchaseHttpTest {
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
    String suffix;
    Browser admin, operator;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString();
        a = branch("a");
        b = branch("b");
        product = product("Maceta", a, b);
        other = product("Sustrato", a);
        admin = account("ADMIN", null);
        operator = account("OPERATOR", a);
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

    String supplier(String name) throws Exception {
        HttpResponse<String> created = admin.post("/purchases/suppliers", Map.of("code", "PRV-" + UUID.randomUUID(), "name", name), null);
        assertThat(created.statusCode()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    static Map<String, Object> line(UUID item, String unit, String quantity, String price, String discount) {
        return Map.of("product", item.toString(), "unit", unit, "quantity", quantity, "unitPrice", price, "discountPercent", discount);
    }

    static Map<String, Object> order(String supplier, Object term, List<?> lines) {
        return Map.of("supplier", supplier, "paymentTermDays", term, "lines", lines);
    }

    /** Creates a draft order of 2 boxes of 12 at 120 with 10% discount. */
    JsonNode draft(String supplier, int term) throws Exception {
        HttpResponse<String> created = operator.post("/purchases/orders",
            order(supplier, term, List.of(line(product, "caja", "2", "120", "10"))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created);
    }

    JsonNode pending(String supplier, int term) throws Exception {
        String id = draft(supplier, term).get("externalId").asText();
        HttpResponse<String> submitted = operator.post("/purchases/orders/" + id + "/submit", Map.of());
        assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(200);
        return read(submitted);
    }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    /** physical quantity, value and average of a balance, as plain strings without trailing zeros. */
    List<String> balance(UUID branch, UUID item) {
        return jdbc.query("SELECT ib.physical_qty,ib.inventory_value,ib.average_base_cost FROM inventory_balance ib "
                + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
                + "WHERE b.external_id=? AND p.external_id=?",
            (r, i) -> List.of(plain(r.getBigDecimal(1)), plain(r.getBigDecimal(2)), plain(r.getBigDecimal(3))), branch, item)
            .stream().findFirst().orElse(List.of("0", "0", "0"));
    }

    static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    /** Purchase receipts posted in this test's branch A; the database is shared by every test of the class. */
    int receipts() {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_operation o JOIN purchases_order po ON po.id=o.purchase_order_id "
            + "JOIN identity_branch b ON b.id=po.branch_id WHERE o.kind='PURCHASE_RECEIPT' AND b.external_id=?", Integer.class, a);
    }

    int orders() {
        return jdbc.queryForObject("SELECT count(*) FROM purchases_order o JOIN identity_branch b ON b.id=o.branch_id "
            + "WHERE b.external_id=?", Integer.class, a);
    }

    /** Public identifiers of the listed orders, in response order. */
    static List<String> ids(JsonNode items) {
        List<String> ids = new java.util.ArrayList<>();
        for (JsonNode item : items) ids.add(item.get("externalId").asText());
        return ids;
    }

    String status(String order) {
        return jdbc.queryForObject("SELECT status FROM purchases_order WHERE external_id=?::uuid", String.class, order);
    }

    void rename(String supplier, String name) throws Exception {
        String code = jdbc.queryForObject("SELECT code FROM purchases_supplier WHERE external_id=?::uuid", String.class, supplier);
        assertThat(admin.post("/purchases/suppliers/" + supplier + "/update", Map.of("code", code, "name", name), null).statusCode())
            .isEqualTo(200);
    }

    @Test void administratorMaintainsMinimumSuppliersAndRejectsDuplicatedCodes() throws Exception {
        String code = "PRV-" + UUID.randomUUID();
        HttpResponse<String> created = admin.post("/purchases/suppliers", Map.of("code", code, "name", "Viveros del Valle"), null);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = read(created).get("externalId").asText();
        assertThat(read(created).get("active").asBoolean()).isTrue();

        // RF-0021-CA-02: a duplicated code is rejected without altering the catalog.
        int before = jdbc.queryForObject("SELECT count(*) FROM purchases_supplier", Integer.class);
        HttpResponse<String> duplicate = admin.post("/purchases/suppliers", Map.of("code", code, "name", "Otro"), null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(read(duplicate).get("code").asText()).isEqualTo("SUPPLIER_CODE_EXISTS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchases_supplier", Integer.class)).isEqualTo(before);

        String second = supplier("Abonos SAS");
        String secondCode = jdbc.queryForObject("SELECT code FROM purchases_supplier WHERE external_id=?::uuid", String.class, second);
        HttpResponse<String> clash = admin.post("/purchases/suppliers/" + second + "/update", Map.of("code", code, "name", "Abonos"), null);
        assertThat(clash.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT code||'|'||name FROM purchases_supplier WHERE external_id=?::uuid", String.class, second))
            .isEqualTo(secondCode + "|Abonos SAS");

        HttpResponse<String> updated = admin.post("/purchases/suppliers/" + id + "/update", Map.of("code", code, "name", "Viveros"), null);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(read(updated).get("name").asText()).isEqualTo("Viveros");

        // INC-012/D02: no fiscal or contact data is accepted.
        HttpResponse<String> fiscal = admin.post("/purchases/suppliers", Map.of("code", "X" + suffix, "name", "X", "nit", "900"), null);
        assertThat(fiscal.statusCode()).isEqualTo(400);

        // Local profiles consult suppliers to use them, but do not maintain them.
        HttpResponse<String> listed = operator.get("/purchases/suppliers");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(read(listed).findValuesAsString("externalId")).contains(id, second);
        assertThat(operator.post("/purchases/suppliers", Map.of("code", "Y" + suffix, "name", "Y"), null).statusCode()).isEqualTo(403);
        Browser manager = account("MANAGER", a);
        assertThat(manager.post("/purchases/suppliers/" + id + "/update", Map.of("code", code, "name", "Z"), null).statusCode())
            .isEqualTo(403);
    }

    @Test void createdOrderKeepsSupplierProductsAndConditionsWithoutChangingStock() throws Exception {
        String supplier = supplier("Viveros del Valle");
        HttpResponse<String> created = operator.post("/purchases/orders", order(supplier, 30, List.of(
            line(product, "caja", "2", "120", "10"), line(other, "unidad", "5", "3.5", "0"))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode body = read(created);
        assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        assertThat(body.get("code").asText()).matches("OC-\\d{6,}");
        assertThat(body.get("supplier").get("externalId").asText()).isEqualTo(supplier);
        assertThat(body.get("supplier").get("name").asText()).isEqualTo("Viveros del Valle");
        assertThat(body.get("paymentTermDays").asInt()).isEqualTo(30);
        assertThat(body.get("dueDate").isNull()).isTrue();
        JsonNode first = body.get("lines").get(0);
        assertThat(first.get("productExternalId").asText()).isEqualTo(product.toString());
        assertThat(List.of(first.get("unit").asText(), first.get("commercialQuantity").asText(), first.get("baseQuantity").asText(),
            first.get("unitPrice").asText(), first.get("discountPercent").asText(), first.get("netAmount").asText()))
            .containsExactly("caja", "2.000", "24.000", "120", "10", "216.00");
        assertThat(first.get("unitBaseCost").isNull()).isTrue();
        assertThat(body.get("lines").get(1).get("discountPercent").asText()).isEqualTo("0");
        assertThat(body.get("totalNet").asText()).isEqualTo("233.50");
        assertThat(body.get("events").get(0).get("toStatus").asText()).isEqualTo("DRAFT");

        // RF-0021: creation increases no stock and posts no entry; the order is consultable with its conditions.
        assertThat(balance(a, product)).containsExactly("0", "0", "0");
        assertThat(receipts()).isZero();
        JsonNode detail = read(operator.get("/purchases/orders/" + body.get("externalId").asText()));
        assertThat(detail.get("lines")).hasSize(2);
        assertThat(detail.get("lines").get(0).get("unitPrice").asText()).isEqualTo("120");
    }

    @Test void invalidConditionsAreRejectedWithoutCreatingTheOrder() throws Exception {
        String supplier = supplier("Viveros");
        int before = orders();
        for (Object term : List.of(-1, 1.5, "30")) {
            HttpResponse<String> response = operator.post("/purchases/orders", order(supplier, term, List.of(line(product, "caja", "1", "120", "10"))));
            assertThat(response.statusCode()).as("term " + term).isEqualTo(400);
            assertThat(read(response).get("detail").asText()).contains("plazo de pago");
        }
        for (String discount : List.of("100.5", "-1", "101")) {
            HttpResponse<String> response = operator.post("/purchases/orders", order(supplier, 0, List.of(line(product, "caja", "1", "120", discount))));
            assertThat(response.statusCode()).as("discount " + discount).isEqualTo(400);
            assertThat(read(response).get("detail").asText()).contains("descuento");
        }
        // The explicit discount is required (0 explicit), as is at least one product.
        HttpResponse<String> missing = operator.post("/purchases/orders", order(supplier, 0, List.of(Map.of(
            "product", product.toString(), "unit", "caja", "quantity", "1", "unitPrice", "120"))));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(operator.post("/purchases/orders", order(supplier, 0, List.of())).statusCode()).isEqualTo(400);
        // Fractions of an indivisible unit and products not enabled in the branch are rejected, too.
        assertThat(operator.post("/purchases/orders", order(supplier, 0, List.of(line(product, "caja", "1.5", "120", "0")))).statusCode())
            .isEqualTo(400);
        UUID foreign = product("Ajeno", b);
        HttpResponse<String> disabled = operator.post("/purchases/orders", order(supplier, 0, List.of(line(foreign, "caja", "1", "1", "0"))));
        assertThat(disabled.statusCode()).isEqualTo(409);
        assertThat(read(disabled).get("code").asText()).isEqualTo("PRODUCT_NOT_OPERABLE");
        assertThat(orders()).isEqualTo(before);
    }

    @Test void supplierNameIsCapturedOnDraftUpdatedOnSubmitAndFrozenAfterwards() throws Exception {
        String supplier = supplier("A");
        String id = draft(supplier, 0).get("externalId").asText();
        rename(supplier, "B");
        assertThat(read(operator.get("/purchases/orders/" + id)).get("supplier").get("name").asText()).isEqualTo("A");

        HttpResponse<String> submitted = operator.post("/purchases/orders/" + id + "/submit", Map.of());
        assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(read(submitted).get("status").asText()).isEqualTo("PENDING");
        assertThat(read(submitted).get("supplier").get("name").asText()).isEqualTo("B");

        rename(supplier, "C");
        assertThat(read(operator.get("/purchases/orders/" + id)).get("supplier").get("name").asText()).isEqualTo("B");
        HttpResponse<String> received = operator.post("/purchases/orders/" + id + "/receive", Map.of());
        assertThat(received.statusCode()).isEqualTo(200);
        assertThat(read(received).get("supplier").get("name").asText()).isEqualTo("B");

        // A pending order cannot be submitted again.
        HttpResponse<String> again = operator.post("/purchases/orders/" + id + "/submit", Map.of());
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(read(again).get("code").asText()).isEqualTo("PURCHASE_STATUS_CONFLICT");
    }

    @Test void receiptPostsValuedEntriesThroughTheLedgerOnceAndComputesTheDueDate() throws Exception {
        String supplier = supplier("Viveros");
        String id = pending(supplier, 0).get("externalId").asText();
        String key = UUID.randomUUID().toString();
        HttpResponse<String> received = operator.post("/purchases/orders/" + id + "/receive", Map.of(), key);
        assertThat(received.statusCode()).as(received.body()).isEqualTo(200);
        JsonNode body = read(received);

        // RF-0023-CA-02: box of 12 at 120 with 10% discount → net 108 per box and cost 9 per base unit.
        assertThat(body.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(body.get("lines").get(0).get("unitBaseCost").asText()).isEqualTo("9.000000");
        assertThat(body.get("lines").get(0).get("exactNetAmount").asText()).isEqualTo("216");
        assertThat(balance(a, product)).containsExactly("24", "216", "9");

        // RF-0024-CA-01: one traceable entry with date, responsible, reason and quantity, linked to the order and line.
        Map<String, Object> movement = jdbc.queryForMap("""
            SELECT o.kind,o.reason,m.direction,m.base_qty,m.historical_base_cost,o.occurred_at,
                (m.purchase_line_id=l.id) AS linked,u.external_id AS actor
            FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id
            JOIN purchases_order po ON po.id=o.purchase_order_id JOIN purchases_line l ON l.order_id=po.id
            JOIN identity_user_account u ON u.id=o.actor_id WHERE po.external_id=?::uuid""", id);
        assertThat(movement.get("kind")).isEqualTo("PURCHASE_RECEIPT");
        assertThat((String) movement.get("reason")).contains(body.get("code").asText());
        assertThat(movement.get("direction")).isEqualTo("IN");
        assertThat(plain((BigDecimal) movement.get("base_qty"))).isEqualTo("24");
        assertThat(plain((BigDecimal) movement.get("historical_base_cost"))).isEqualTo("9");
        assertThat(movement.get("linked")).isEqualTo(true);
        assertThat(body.get("receiptOperationExternalId").isNull()).isFalse();
        assertThat(body.get("receivedBy").isNull()).isFalse();

        // A zero term falls due on the confirmed receipt date in Bogotá.
        LocalDate receiptDate = OffsetDateTime.parse(body.get("receivedAt").asText()).atZoneSameInstant(ZoneId.of("America/Bogota")).toLocalDate();
        assertThat(body.get("dueDate").asText()).isEqualTo(receiptDate.toString());

        // RF-0024-CA-02: the same request returns the stored result; another request is an identifiable conflict.
        HttpResponse<String> repeated = operator.post("/purchases/orders/" + id + "/receive", Map.of(), key);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(read(repeated).get("receiptOperationExternalId")).isEqualTo(body.get("receiptOperationExternalId"));
        HttpResponse<String> conflict = operator.post("/purchases/orders/" + id + "/receive", Map.of());
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(read(conflict).get("code").asText()).isEqualTo("PURCHASE_ALREADY_RECEIVED");
        assertThat(receipts()).isEqualTo(1);
        assertThat(balance(a, product)).containsExactly("24", "216", "9");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id "
            + "JOIN purchases_order po ON po.id=o.purchase_order_id WHERE po.external_id=?::uuid", Integer.class, id)).isEqualTo(1);
    }

    @Test void paymentTermCountsFromTheReceiptAndAveragesWithPreviousStock() throws Exception {
        String supplier = supplier("Viveros");
        operator.post("/purchases/orders/" + pending(supplier, 0).get("externalId").asText() + "/receive", Map.of());
        String id = operator.post("/purchases/orders", order(supplier, 30, List.of(line(product, "unidad", "12", "12", "0"))))
            .body().transform(this::externalId);
        operator.post("/purchases/orders/" + id + "/submit", Map.of());
        JsonNode received = read(operator.post("/purchases/orders/" + id + "/receive", Map.of()));
        LocalDate receiptDate = OffsetDateTime.parse(received.get("receivedAt").asText()).atZoneSameInstant(ZoneId.of("America/Bogota")).toLocalDate();
        assertThat(received.get("dueDate").asText()).isEqualTo(receiptDate.plusDays(30).toString());
        // RF-0026 through the shared ledger: (216 + 144) / 36 = 10.
        assertThat(balance(a, product)).containsExactly("36", "360", "10");
    }

    String externalId(String body) { return json.readTree(body).get("externalId").asText(); }

    @Test void partialOrInvalidReceiptsChangeNothing() throws Exception {
        String supplier = supplier("Viveros");
        String draft = draft(supplier, 0).get("externalId").asText();
        HttpResponse<String> early = operator.post("/purchases/orders/" + draft + "/receive", Map.of());
        assertThat(early.statusCode()).isEqualTo(409);
        assertThat(read(early).get("code").asText()).isEqualTo("PURCHASE_STATUS_CONFLICT");

        String id = pending(supplier, 0).get("externalId").asText();
        HttpResponse<String> partial = operator.post("/purchases/orders/" + id + "/receive",
            Map.of("lines", List.of(Map.of("lineNo", 1, "quantity", "1"))));
        assertThat(partial.statusCode()).isEqualTo(400);
        assertThat(read(partial).get("code").asText()).isEqualTo("PARTIAL_RECEIPT_NOT_SUPPORTED");
        assertThat(status(id)).isEqualTo("PENDING");

        // A product disabled after ordering blocks the whole receipt: no line is received.
        jdbc.update("UPDATE catalog_branch_product SET enabled=false WHERE product_id=(SELECT id FROM catalog_product WHERE external_id=?)", product);
        HttpResponse<String> blocked = operator.post("/purchases/orders/" + id + "/receive", Map.of());
        assertThat(blocked.statusCode()).isEqualTo(409);
        assertThat(read(blocked).get("code").asText()).isEqualTo("PRODUCT_NOT_OPERABLE");
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(receipts()).isZero();
        assertThat(balance(a, product)).containsExactly("0", "0", "0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchases_line l JOIN purchases_order o ON o.id=l.order_id "
            + "WHERE o.external_id=?::uuid AND l.historical_base_cost IS NOT NULL", Integer.class, id)).isZero();
    }

    @Test void onlyTheOperatorOfTheBranchExecutesPurchases() throws Exception {
        String supplier = supplier("Viveros");
        Map<String, Object> body = order(supplier, 0, List.of(line(product, "caja", "1", "120", "0")));
        int before = orders();
        Browser manager = account("MANAGER", a);
        for (Browser forbidden : List.of(manager, admin)) {
            HttpResponse<String> response = forbidden.post("/purchases/orders", body);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(read(response).get("code").asText()).isEqualTo("PURCHASE_FORBIDDEN");
        }
        assertThat(orders()).isEqualTo(before);

        String id = pending(supplier, 0).get("externalId").asText();
        assertThat(manager.post("/purchases/orders/" + id + "/receive", Map.of()).statusCode()).isEqualTo(403);
        // An operator of another branch cannot receive it, and learns nothing about it.
        Browser foreign = account("OPERATOR", b);
        HttpResponse<String> response = foreign.post("/purchases/orders/" + id + "/receive", Map.of());
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("OC-");
        assertThat(foreign.get("/purchases/orders/" + id).statusCode()).isEqualTo(403);
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(receipts()).isZero();

        // An inactive branch admits no new purchase.
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        HttpResponse<String> inactive = operator.post("/purchases/orders", body);
        assertThat(inactive.statusCode()).isEqualTo(409);
        assertThat(read(inactive).get("code").asText()).isEqualTo("BRANCH_INACTIVE");
        jdbc.update("UPDATE identity_branch SET active=true WHERE external_id=?", a);

        // A missing idempotency key is rejected before any effect.
        assertThat(operator.post("/purchases/orders", body, null).statusCode()).isEqualTo(400);
    }

    @Test void historyIdentifiesPurchasesBySupplierAndProductAndDistinguishesStates() throws Exception {
        String viveros = supplier("Viveros");
        String abonos = supplier("Abonos");
        String drafted = draft(viveros, 0).get("externalId").asText();
        String waiting = pending(viveros, 0).get("externalId").asText();
        String received = pending(abonos, 0).get("externalId").asText();
        operator.post("/purchases/orders/" + received + "/receive", Map.of());
        String withOther = operator.post("/purchases/orders", order(abonos, 0, List.of(line(other, "unidad", "1", "1", "0"))))
            .body().transform(this::externalId);
        // RF-0022 owns cancellation; a cancelled order is recorded here directly to prove the history never shows it as received.
        jdbc.update("UPDATE purchases_order SET status='CANCELLED' WHERE external_id=?::uuid", withOther);

        JsonNode bySupplier = read(operator.get("/purchases/orders?supplier=" + viveros));
        assertThat(ids(bySupplier.get("items"))).containsExactly(waiting, drafted);
        assertThat(bySupplier.get("summary").get("orders").asInt()).isEqualTo(2);
        assertThat(bySupplier.get("summary").get("received").asInt()).isZero();

        JsonNode byProduct = read(operator.get("/purchases/orders?product=" + product));
        assertThat(ids(byProduct.get("items"))).containsExactlyInAnyOrder(drafted, waiting, received);
        JsonNode byOther = read(operator.get("/purchases/orders?product=" + other));
        assertThat(ids(byOther.get("items"))).containsExactly(withOther);
        assertThat(byOther.get("items").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(byOther.get("summary").get("cancelled").asInt()).isEqualTo(1);
        assertThat(byOther.get("summary").get("received").asInt()).isZero();

        JsonNode all = read(operator.get("/purchases/orders"));
        JsonNode summary = all.get("summary");
        assertThat(List.of(summary.get("orders").asInt(), summary.get("drafts").asInt(), summary.get("pending").asInt(),
            summary.get("received").asInt(), summary.get("cancelled").asInt())).containsExactly(4, 1, 1, 1, 1);
        assertThat(ids(read(operator.get("/purchases/orders?status=RECEIVED")).get("items")))
            .containsExactly(received);
        assertThat(operator.get("/purchases/orders?status=PAID").statusCode()).isEqualTo(400);

        // Managers read their own branch; administrators the network or one branch.
        Browser manager = account("MANAGER", a);
        assertThat(read(manager.get("/purchases/orders")).get("summary").get("orders").asInt()).isEqualTo(4);
        assertThat(manager.get("/purchases/orders?branch=" + b).statusCode()).isEqualTo(403);
        assertThat(operator.get("/purchases/orders?branch=" + b).statusCode()).isEqualTo(403);
        JsonNode network = read(admin.get("/purchases/orders?supplier=" + abonos));
        assertThat(network.get("branch").isNull()).isTrue();
        assertThat(ids(network.get("items"))).containsExactlyInAnyOrder(received, withOther);
        assertThat(read(admin.get("/purchases/orders?branch=" + b)).get("items")).isEmpty();
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
