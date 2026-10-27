package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.service.InventoryLedger;
import com.optiplant.inventory.service.InventoryLedger.Cost;
import com.optiplant.inventory.service.InventoryLedger.Direction;
import com.optiplant.inventory.service.InventoryLedger.Kind;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=movements-initial", "app.admin-password=private initial password"})
class InventoryMovementHttpTest {
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
    @Autowired InventoryLedger ledger;
    @Autowired TransactionTemplate transactions;
    UUID a, b, product;
    String suffix;

    @BeforeEach void data() {
        suffix = UUID.randomUUID().toString();
        a = branch("a");
        b = branch("b");
        product = product(a, b);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    /** An indivisible product with base unit "unidad" and commercial unit "caja" of 12, enabled in the given branches. */
    UUID product(UUID... branches) {
        UUID id = UUID.randomUUID();
        String sku = "SKU-" + UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,'Maceta','unidad',false)", id, sku);
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
        Browser browser = new Browser(code);
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password"), null).statusCode())
            .isEqualTo(200);
        return browser;
    }

    static Map<String, Object> movement(String kind, Object... pairs) {
        Map<String, Object> body = new HashMap<>(Map.of("kind", kind));
        for (int i = 0; i < pairs.length; i += 2) body.put((String) pairs[i], pairs[i + 1]);
        return body;
    }

    Map<String, Object> entry(UUID item, String unit, String quantity, String cost, String reason) {
        Map<String, Object> body = movement("MANUAL_ENTRY", "product", item.toString(), "unit", unit, "quantity", quantity, "reason", reason);
        if (cost != null) body.put("unitCost", cost);
        return body;
    }

    Map<String, Object> exit(UUID item, String quantity) {
        return movement("MANUAL_EXIT", "product", item.toString(), "unit", "unidad", "quantity", quantity, "reason", "Merma por daño");
    }

    /** physical quantity, value and average of a balance, as plain strings without trailing zeros. */
    List<String> balance(UUID branch, UUID item) {
        return jdbc.query("SELECT ib.physical_qty,ib.inventory_value,ib.average_base_cost FROM inventory_balance ib "
                + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
                + "WHERE b.external_id=? AND p.external_id=?",
            (r, i) -> List.of(plain(r.getBigDecimal(1)), plain(r.getBigDecimal(2)), plain(r.getBigDecimal(3))), branch, item)
            .stream().findFirst().orElse(List.of("0", "0", "0"));
    }

    static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    List<String> components(String movement) {
        return jdbc.queryForList("SELECT c.component_kind||' '||trim_scale(c.amount) FROM inventory_valuation_component c "
            + "JOIN inventory_movement m ON m.id=c.movement_id WHERE m.external_id=?::uuid ORDER BY c.component_kind", String.class, movement);
    }

    /** Posts a valued entry through the shared ledger, as a purchase receipt will. */
    void valuedEntry(UUID branch, UUID item, String quantity, String cost) {
        transactions.executeWithoutResult(status -> ledger.post(new InventoryLedger.Operation(Kind.MANUAL_ENTRY, id("identity_branch", branch),
            jdbc.queryForObject("SELECT min(id) FROM identity_user_account", Long.class), "Compra de prueba", null, null, null,
            List.of(new InventoryLedger.Line(id("catalog_product", item), unitId(item, "unidad"), BigDecimal.ONE,
                new BigDecimal(quantity), new BigDecimal(quantity), Direction.IN, new Cost.Explicit(new BigDecimal(cost)),
                null, null, null)))));
    }

    long id(String table, UUID external) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE external_id=?", Long.class, external);
    }

    long unitId(UUID item, String name) {
        return jdbc.queryForObject("SELECT u.id FROM catalog_product_unit u JOIN catalog_product p ON p.id=u.product_id "
            + "WHERE p.external_id=? AND u.name=?", Long.class, item, name);
    }

    @Test void operatorEntryAdoptsJustifiedCostWithoutStockAndUsesAverageAfterwards() throws Exception {
        Browser operator = account("OPERATOR", a);
        int movements = count("inventory_movement");
        var missing = operator.post("/inventory/movements", entry(product, "unidad", "10", null, "Ingreso inicial"));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(code(missing)).isEqualTo("UNIT_COST_REQUIRED");
        assertThat(count("inventory_movement")).isEqualTo(movements);

        var first = operator.post("/inventory/movements", entry(product, "unidad", "10", "100", "Ingreso inicial"));
        assertThat(first.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(first.body());
        assertThat(body.get("kind").asText()).isEqualTo("MANUAL_ENTRY");
        assertThat(body.get("direction").asText()).isEqualTo("IN");
        assertThat(body.get("baseQuantity").asText()).isEqualTo("10.000");
        assertThat(body.get("resultingPhysicalQuantity").asText()).isEqualTo("10.000");
        assertThat(body.get("responsibleCode").asText()).isEqualTo(operator.code);
        assertThat(first.body()).doesNotContain("cost", "Cost", "value", "average");
        assertThat(balance(a, product)).containsExactly("10", "1000", "100");

        var withCost = operator.post("/inventory/movements", entry(product, "caja", "1", "90", "Ingreso con costo"));
        assertThat(withCost.statusCode()).isEqualTo(400);
        assertThat(code(withCost)).isEqualTo("UNIT_COST_NOT_ALLOWED");
        var box = operator.post("/inventory/movements", entry(product, "caja", "1", null, "Ingreso de una caja"));
        assertThat(box.statusCode()).isEqualTo(201);
        assertThat(json.readTree(box.body()).get("baseQuantity").asText()).isEqualTo("12.000");
        assertThat(balance(a, product)).containsExactly("22", "2200", "100");
        assertThat(balance(b, product)).containsExactly("0", "0", "0");

        Map<String, Object> stored = jdbc.queryForMap("SELECT o.kind,o.reason,u.code,m.commercial_qty,m.base_qty,m.factor_snapshot,"
            + "m.unit_name_snapshot,m.historical_base_cost FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id "
            + "JOIN identity_user_account u ON u.id=o.actor_id WHERE m.external_id=?::uuid", json.readTree(box.body()).get("movementExternalId").asText());
        assertThat(stored.get("reason")).isEqualTo("Ingreso de una caja");
        assertThat(stored.get("code")).isEqualTo(operator.code);
        assertThat(plain((BigDecimal) stored.get("factor_snapshot"))).isEqualTo("12");
        assertThat(stored.get("unit_name_snapshot")).isEqualTo("caja");
        assertThat(plain((BigDecimal) stored.get("historical_base_cost"))).isEqualTo("100");
        assertThat(components(json.readTree(box.body()).get("movementExternalId").asText())).containsExactly("BASE 1200");

        var fraction = operator.post("/inventory/movements", entry(product, "unidad", "1.5", null, "Fracción"));
        assertThat(fraction.statusCode()).isEqualTo(400);
    }

    @Test void weightedAverageEntriesAndOrdinaryExitFollowRf0026Ca01() throws Exception {
        valuedEntry(a, product, "10", "100");
        valuedEntry(a, product, "5", "130");
        assertThat(balance(a, product)).containsExactly("15", "1650", "110");
        var exit = account("OPERATOR", a).post("/inventory/movements", exit(product, "3"));
        assertThat(exit.statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("12", "1320", "110");
        assertThat(components(json.readTree(exit.body()).get("movementExternalId").asText())).containsExactly("BASE -330");
    }

    @Test void depletingExitAbsorbsTheRoundingResidualOfRf0026Ca03() throws Exception {
        valuedEntry(a, product, "1", "4");
        valuedEntry(a, product, "2", "3");
        assertThat(balance(a, product)).containsExactly("3", "10", "3.333333");
        Browser operator = account("OPERATOR", a);
        assertThat(operator.post("/inventory/movements", exit(product, "1")).statusCode()).isEqualTo(201);
        assertThat(operator.post("/inventory/movements", exit(product, "1")).statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("1", "3.333334", "3.333333");
        var last = operator.post("/inventory/movements", exit(product, "1"));
        assertThat(components(json.readTree(last.body()).get("movementExternalId").asText()))
            .containsExactly("BASE -3.333333", "FINAL_ROUNDING -0.000001");
        assertThat(balance(a, product)).containsExactly("0", "0", "3.333333");
    }

    @Test void exitBeyondPhysicalStockIsRejectedWithoutAnyTrace() throws Exception {
        valuedEntry(a, product, "2", "5");
        int operations = count("inventory_operation");
        int movements = count("inventory_movement");
        int replays = count("app_idempotency");
        var exceeded = account("OPERATOR", a).post("/inventory/movements", exit(product, "3"));
        assertThat(exceeded.statusCode()).isEqualTo(409);
        assertThat(code(exceeded)).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(count("inventory_operation")).isEqualTo(operations);
        assertThat(count("inventory_movement")).isEqualTo(movements);
        assertThat(count("app_idempotency")).isEqualTo(replays);
        assertThat(balance(a, product)).containsExactly("2", "10", "5");
    }

    @Test void managerCorrectionAndCountAdjustmentFollowRf0026Ca04() throws Exception {
        UUID other = product(a);
        Browser operator = account("OPERATOR", a);
        Browser manager = account("MANAGER", a);
        String original = null;
        for (UUID item : List.of(product, other)) {
            var registered = operator.post("/inventory/movements", entry(item, "unidad", "10", "10", "Entrada registrada con 10"));
            if (item == product) original = json.readTree(registered.body()).get("movementExternalId").asText();
            assertThat(operator.post("/inventory/movements", exit(item, "6")).statusCode()).isEqualTo(201);
            valuedEntry(a, item, "4", "20");
            assertThat(balance(a, item)).containsExactly("8", "120", "15");
        }
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM inventory_movement WHERE external_id=?::uuid", original);

        var correction = manager.post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "OUT", "reference", original,
            "quantity", "2", "reason", "Llegaron 8 y no 10"));
        assertThat(correction.statusCode()).isEqualTo(201);
        JsonNode corrected = json.readTree(correction.body());
        assertThat(corrected.get("referenceExternalId").asText()).isEqualTo(original);
        assertThat(balance(a, product)).containsExactly("6", "100", "16.666667");
        assertThat(components(corrected.get("movementExternalId").asText())).containsExactly("BASE -20");
        assertThat(jdbc.queryForMap("SELECT * FROM inventory_movement WHERE external_id=?::uuid", original)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operation o JOIN inventory_operation r ON r.id=o.original_operation_id "
            + "JOIN inventory_movement m ON m.operation_id=r.id WHERE o.kind='ENTRY_CORRECTION' AND m.external_id=?::uuid", Integer.class, original))
            .isEqualTo(1);

        var adjustment = manager.post("/inventory/movements", movement("COUNT_ADJUSTMENT", "direction", "OUT", "product", other.toString(),
            "unit", "unidad", "quantity", "2", "reason", "Conteo físico"));
        assertThat(adjustment.statusCode()).isEqualTo(201);
        assertThat(balance(a, other)).containsExactly("6", "90", "15");

        var exceeding = manager.post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "OUT", "reference", original,
            "quantity", "9", "reason", "Exceso"));
        assertThat(exceeding.statusCode()).isEqualTo(409);
        assertThat(code(exceeding)).isEqualTo("COMPENSATION_EXCEEDS_PENDING");
    }

    @Test void depletingCorrectionRecordsASeparateValuationDifferenceOfRf0026Ca05() throws Exception {
        Browser operator = account("OPERATOR", a);
        Browser manager = account("MANAGER", a);
        String original = json.readTree(operator.post("/inventory/movements",
            entry(product, "unidad", "10", "10", "Entrada registrada con 10")).body()).get("movementExternalId").asText();
        operator.post("/inventory/movements", exit(product, "6"));
        valuedEntry(a, product, "4", "20");
        operator.post("/inventory/movements", exit(product, "6"));
        assertThat(balance(a, product)).containsExactly("2", "30", "15");

        var tooMany = manager.post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "OUT", "reference", original,
            "quantity", "3", "reason", "Unidades ficticias"));
        assertThat(tooMany.statusCode()).isEqualTo(409);
        assertThat(code(tooMany)).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(balance(a, product)).containsExactly("2", "30", "15");

        var correction = manager.post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "OUT", "reference", original,
            "quantity", "2", "reason", "Unidades ficticias"));
        assertThat(correction.statusCode()).isEqualTo(201);
        assertThat(components(json.readTree(correction.body()).get("movementExternalId").asText()))
            .containsExactly("BASE -20", "HISTORICAL_CORRECTION -10");
        assertThat(balance(a, product)).containsExactly("0", "0", "15");

        JsonNode history = json.readTree(manager.get("/inventory/branches/" + a + "/movements").body());
        JsonNode latest = history.get("items").get(0);
        assertThat(latest.get("kind").asText()).isEqualTo("ENTRY_CORRECTION");
        assertThat(latest.get("referenceExternalId").asText()).isEqualTo(original);
        assertThat(latest.get("valuation").get("baseAmount").asText()).isEqualTo("-20.00");
        assertThat(latest.get("valuation").get("differenceKind").asText()).isEqualTo("HISTORICAL_CORRECTION");
        assertThat(latest.get("valuation").get("differenceAmount").asText()).isEqualTo("-10.00");
        assertThat(latest.get("valuation").get("totalAmount").asText()).isEqualTo("-30.00");
    }

    /** L12: a correction leaving units never leaves a negative value; the excess is a separate valuation difference. */
    @Test void correctionThatWouldLeaveNegativeValueRecordsTheExcessAsAValuationDifference() throws Exception {
        Browser operator = account("OPERATOR", a);
        valuedEntry(a, product, "10", "10");
        assertThat(operator.post("/inventory/movements", entry(product, "unidad", "10", null, "Ingreso al promedio")).statusCode()).isEqualTo(201);
        valuedEntry(a, product, "10", "1000");
        assertThat(balance(a, product)).containsExactly("30", "10200", "340");
        assertThat(operator.post("/inventory/movements", exit(product, "25")).statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("5", "1700", "340");
        String expensive = jdbc.queryForObject("SELECT m.external_id::text FROM inventory_movement m JOIN catalog_product p ON p.id=m.product_id "
            + "WHERE p.external_id=? AND m.historical_base_cost=1000", String.class, product);

        var correction = account("MANAGER", a).post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "OUT",
            "reference", expensive, "quantity", "4", "reason", "Costo de entrada mal registrado"));
        assertThat(correction.statusCode()).isEqualTo(201);
        assertThat(components(json.readTree(correction.body()).get("movementExternalId").asText()))
            .containsExactly("BASE -4000", "HISTORICAL_CORRECTION 2300");
        assertThat(balance(a, product)).containsExactly("1", "0", "0");
    }

    /** L14 (1-A): a return later corrected no longer consumes the returnable quantity of its sale. */
    @Test void correctedReturnReleasesItsUnitsForALaterReturn() throws Exception {
        valuedEntry(a, product, "10", "7");
        String sale = sale(a, product, "5");
        Browser operator = account("OPERATOR", a);
        String wrong = json.readTree(operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale,
            "quantity", "2", "reason", "Devolución registrada por error")).body()).get("movementExternalId").asText();
        assertThat(account("MANAGER", a).post("/inventory/movements", movement("ENTRY_CORRECTION", "reference", wrong,
            "quantity", "2", "reason", "El cliente no devolvió nada")).statusCode()).isEqualTo(201);
        JsonNode saleEntry = null;
        for (JsonNode item : json.readTree(operator.get("/inventory/branches/" + a + "/movements").body()).get("items")) {
            if (item.get("externalId").asText().equals(sale)) saleEntry = item;
        }
        assertThat(saleEntry.get("pendingQuantity").asText()).isEqualTo("5.000");
        assertThat(code(operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale, "quantity", "6",
            "reason", "Devolución completa")))).isEqualTo("COMPENSATION_EXCEEDS_PENDING");
        assertThat(operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale, "quantity", "5",
            "reason", "Devolución completa")).statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("10", "70", "7");
    }

    /** L14 (2-A): corrections only remove units registered in excess; missing units enter as a new movement. */
    @Test void correctionsOnlyDecreaseAnIdentifiedEntry() throws Exception {
        Browser operator = account("OPERATOR", a);
        String original = json.readTree(operator.post("/inventory/movements", entry(product, "unidad", "8", "10",
            "Llegaron 10, se registraron 8")).body()).get("movementExternalId").asText();
        int operations = count("inventory_operation");
        var upward = account("MANAGER", a).post("/inventory/movements", movement("ENTRY_CORRECTION", "direction", "IN",
            "reference", original, "quantity", "2", "reason", "Faltaron 2"));
        assertThat(upward.statusCode()).isEqualTo(400);
        assertThat(json.readTree(upward.body()).get("detail").asText()).contains("nuevo ingreso");
        assertThat(count("inventory_operation")).isEqualTo(operations);
        assertThat(balance(a, product)).containsExactly("8", "80", "10");
    }

    @Test void returnLinkedToASaleNeverExceedsWhatWasSoldAsInRf0016Ca03() throws Exception {
        valuedEntry(a, product, "10", "7");
        String sale = sale(a, product, "5");
        Browser operator = account("OPERATOR", a);
        var first = operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale, "quantity", "2", "reason", "Cliente devolvió"));
        assertThat(first.statusCode()).isEqualTo(201);
        int operations = count("inventory_operation");
        var four = operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale, "quantity", "4", "reason", "Cliente devolvió"));
        assertThat(four.statusCode()).isEqualTo(409);
        assertThat(code(four)).isEqualTo("COMPENSATION_EXCEEDS_PENDING");
        assertThat(count("inventory_operation")).isEqualTo(operations);
        var three = operator.post("/inventory/movements", movement("SALE_RETURN", "reference", sale, "quantity", "3", "reason", "Cliente devolvió"));
        assertThat(three.statusCode()).isEqualTo(201);
        assertThat(components(json.readTree(three.body()).get("movementExternalId").asText())).containsExactly("BASE 21");
        assertThat(balance(a, product)).containsExactly("10", "70", "7");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE sale_line_id IS NOT NULL AND original_movement_id="
            + "(SELECT id FROM inventory_movement WHERE external_id=?::uuid)", Integer.class, sale)).isEqualTo(2);

        var unreferenced = operator.post("/inventory/movements", movement("SALE_RETURN", "product", product.toString(), "unit", "unidad",
            "quantity", "1", "reason", "Devolución sin ticket"));
        assertThat(unreferenced.statusCode()).isEqualTo(201);
        var notASale = operator.post("/inventory/movements", movement("SALE_RETURN", "reference",
            json.readTree(first.body()).get("movementExternalId").asText(), "quantity", "1", "reason", "Referencia inválida"));
        assertThat(code(notASale)).isEqualTo("RETURN_REFERENCE_INVALID");
    }

    /** Seeds a sale document and posts its exit through the shared ledger, as the sales module will; returns the movement. */
    String sale(UUID branch, UUID item, String quantity) {
        long branchId = id("identity_branch", branch);
        long productId = id("catalog_product", item);
        long unit = unitId(item, "unidad");
        long actor = jdbc.queryForObject("SELECT min(id) FROM identity_user_account", Long.class);
        long list = jdbc.queryForObject("INSERT INTO catalog_price_list(external_id,code,name) VALUES (?,?,'General') RETURNING id",
            Long.class, UUID.randomUUID(), "L" + UUID.randomUUID());
        return transactions.execute(status -> {
            InventoryLedger.Balance locked = ledger.lock(branchId, productId);
            long saleId = jdbc.queryForObject("INSERT INTO sales_sale(external_id,code,branch_id,confirmed_by,confirmed_at) "
                + "VALUES (?,?,?,?,CURRENT_TIMESTAMP) RETURNING id", Long.class, UUID.randomUUID(), "V" + UUID.randomUUID(), branchId, actor);
            long line = jdbc.queryForObject("""
                INSERT INTO sales_line(sale_id,line_no,product_id,product_unit_id,price_list_id,list_name_snapshot,sku_snapshot,
                    product_name_snapshot,unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,
                    net_amount,historical_base_cost)
                VALUES (?,1,?,?,?,'General','SKU','Maceta','unidad',1,?,?,12,0,?*12,?) RETURNING id""", Long.class,
                saleId, productId, unit, list, new BigDecimal(quantity), new BigDecimal(quantity), new BigDecimal(quantity), locked.average());
            var posted = ledger.post(new InventoryLedger.Operation(Kind.SALE, branchId, actor, "Venta confirmada", null, saleId, null,
                List.of(new InventoryLedger.Line(productId, unit, BigDecimal.ONE, new BigDecimal(quantity), new BigDecimal(quantity),
                    Direction.OUT, new Cost.Average(), null, line, null))));
            return posted.movements().getFirst().externalId().toString();
        });
    }

    @Test void rolesBranchStateAndCatalogLimitWhoRegistersWhat() throws Exception {
        valuedEntry(a, product, "5", "2");
        Browser admin = account("ADMIN", null);
        Browser manager = account("MANAGER", a);
        Browser operator = account("OPERATOR", a);
        assertThat(code(admin.post("/inventory/movements", entry(product, "unidad", "1", null, "Ingreso")))).isEqualTo("MOVEMENT_FORBIDDEN");
        assertThat(code(manager.post("/inventory/movements", entry(product, "unidad", "1", null, "Ingreso")))).isEqualTo("MOVEMENT_FORBIDDEN");
        assertThat(code(manager.post("/inventory/movements", exit(product, "1")))).isEqualTo("MOVEMENT_FORBIDDEN");
        var operatorAdjust = operator.post("/inventory/movements", movement("COUNT_ADJUSTMENT", "direction", "IN",
            "product", product.toString(), "unit", "unidad", "quantity", "1", "reason", "Conteo"));
        assertThat(operatorAdjust.statusCode()).isEqualTo(403);

        // RF-0016-CA-02 / RF-0017-CA-02: the manager adjusts only the session's own branch.
        valuedEntry(b, product, "4", "2");
        var increase = manager.post("/inventory/movements", movement("COUNT_ADJUSTMENT", "direction", "IN",
            "product", product.toString(), "unit", "unidad", "quantity", "2", "reason", "Conteo semanal"));
        assertThat(increase.statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("7", "14", "2");
        var decrease = manager.post("/inventory/movements", movement("COUNT_ADJUSTMENT", "direction", "OUT",
            "product", product.toString(), "unit", "unidad", "quantity", "3", "reason", "Conteo semanal"));
        assertThat(decrease.statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("4", "8", "2");
        assertThat(balance(b, product)).containsExactly("4", "8", "2");
        JsonNode adjustment = json.readTree(manager.get("/inventory/branches/" + a + "/movements").body()).get("items").get(0);
        assertThat(adjustment.get("responsible").get("code").asText()).isEqualTo(manager.code);
        assertThat(adjustment.get("reason").asText()).isEqualTo("Conteo semanal");
        assertThat(adjustment.get("commercialQuantity").asText()).isEqualTo("3.000");
        assertThat(adjustment.get("occurredAt").asText()).isNotBlank();

        UUID disabled = product(b);
        assertThat(code(operator.post("/inventory/movements", exit(disabled, "1")))).isEqualTo("PRODUCT_NOT_OPERABLE");
        assertThat(code(operator.post("/inventory/movements", entry(product, "docena", "1", null, "Ingreso")))).isEqualTo("PRODUCT_UNIT_NOT_FOUND");
        assertThat(operator.post("/inventory/movements", movement("MANUAL_EXIT", "product", product.toString(), "unit", "unidad",
            "quantity", "1", "reason", " ")).statusCode()).isEqualTo(400);
        assertThat(operator.post("/inventory/movements", movement("SALE", "product", product.toString(), "unit", "unidad",
            "quantity", "1", "reason", "Venta manual")).statusCode()).isEqualTo(400);
        assertThat(operator.post("/inventory/movements", movement("MANUAL_EXIT", "product", product.toString(), "unit", "unidad",
            "quantity", "0", "reason", "Cero")).statusCode()).isEqualTo(400);
        var noKey = operator.send("/inventory/movements", exit(product, "1"), null);
        assertThat(code(noKey)).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        var inactive = operator.post("/inventory/movements", exit(product, "1"));
        assertThat(code(inactive)).isEqualTo("BRANCH_INACTIVE");
        assertThat(balance(a, product)).containsExactly("4", "8", "2");
        assertThat(new Browser("anonymous").send("/inventory/movements", exit(product, "1"), "k").statusCode()).isIn(401, 403);
    }

    @Test void repeatedRequestReplaysTheResultAndADifferentRequestUnderTheSameKeyConflicts() throws Exception {
        valuedEntry(a, product, "5", "2");
        Browser operator = account("OPERATOR", a);
        var first = operator.send("/inventory/movements", exit(product, "1"), "retry-1");
        var again = operator.send("/inventory/movements", exit(product, "1"), "retry-1");
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(json.readTree(again.body())).isEqualTo(json.readTree(first.body()));
        assertThat(balance(a, product)).containsExactly("4", "8", "2");
        var different = operator.send("/inventory/movements", exit(product, "2"), "retry-1");
        assertThat(code(different)).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(balance(a, product)).containsExactly("4", "8", "2");
        // The key is scoped by actor: another operator may reuse the same text.
        assertThat(account("OPERATOR", a).send("/inventory/movements", exit(product, "1"), "retry-1").statusCode()).isEqualTo(201);
        assertThat(balance(a, product)).containsExactly("3", "6", "2");
    }

    @Test void concurrentExitsNeverConsumeTheSameUnits() throws Exception {
        valuedEntry(a, product, "3", "1");
        Browser first = account("OPERATOR", a);
        Browser second = account("OPERATOR", a);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (Browser browser : List.of(first, second)) {
            Callable<Integer> call = () -> { start.await(); return browser.post("/inventory/movements", exit(product, "2")).statusCode(); };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) statuses.add(result.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(balance(a, product)).containsExactly("1", "1", "1");
    }

    @Test void historyKeepsEveryMovementWithAuditDataAndHidesValuationFromOperators() throws Exception {
        Browser operator = account("OPERATOR", a);
        operator.post("/inventory/movements", entry(product, "unidad", "5", "4", "Ingreso inicial"));
        operator.post("/inventory/movements", exit(product, "2"));
        operator.post("/inventory/movements", exit(product, "1"));

        var own = operator.get("/inventory/branches/" + a + "/movements");
        assertThat(own.statusCode()).isEqualTo(200);
        JsonNode items = json.readTree(own.body()).get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("commercialQuantity").asText()).isEqualTo("1.000");
        assertThat(items.get(2).get("kind").asText()).isEqualTo("MANUAL_ENTRY");
        assertThat(items.get(2).get("reason").asText()).isEqualTo("Ingreso inicial");
        assertThat(items.get(2).get("responsible").get("code").asText()).isEqualTo(operator.code);
        assertThat(items.get(2).get("pendingQuantity").asText()).isEqualTo("5.000");
        assertThat(items.get(0).get("pendingQuantity").isNull()).isTrue();
        for (JsonNode item : items) assertThat(item.get("valuation").isNull()).isTrue();
        assertThat(own.body()).doesNotContain("\"id\"", "branch_id", "password");
        assertThat(json.readTree(operator.get("/inventory/branches/" + a + "/movements?limit=1").body()).get("items")).hasSize(1);
        assertThat(operator.get("/inventory/branches/" + a + "/movements?limit=0").statusCode()).isEqualTo(400);
        assertThat(json.readTree(operator.get("/inventory/branches/" + a + "/movements?product=" + product(a)).body()).get("items")).isEmpty();
        assertThat(operator.get("/inventory/branches/" + b + "/movements").statusCode()).isEqualTo(403);

        Browser admin = account("ADMIN", null);
        JsonNode adminItems = json.readTree(admin.get("/inventory/branches/" + a + "/movements").body()).get("items");
        assertThat(adminItems.get(2).get("valuation").get("unitCost").asText()).isEqualTo("4.000000");
        assertThat(adminItems.get(2).get("valuation").get("baseAmount").asText()).isEqualTo("20.00");
        assertThat(adminItems.get(0).get("valuation").get("baseAmount").asText()).isEqualTo("-4.00");
        assertThat(admin.get("/inventory/branches/" + UUID.randomUUID() + "/movements").statusCode()).isEqualTo(404);
        assertThat(json.readTree(account("MANAGER", a).get("/inventory/branches/" + a + "/movements").body())
            .get("items").get(0).get("valuation").isNull()).isFalse();
    }

    @Test void runtimeRoleCannotEditOrDeleteConfirmedHistory() {
        for (String table : List.of("inventory_operation", "inventory_movement", "inventory_valuation_component")) {
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'UPDATE')", Boolean.class, table)).isFalse();
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'DELETE')", Boolean.class, table)).isFalse();
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'INSERT')", Boolean.class, table)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'inventory_balance','UPDATE')", Boolean.class)).isTrue();
    }

    @Test void browserOriginMayPreflightTheIdempotencyKeyOnlyFromTheConfiguredFrontend() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (String origin : List.of("http://localhost:5173", "http://untrusted.example")) {
            var preflight = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/inventory/movements"))
                .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,idempotency-key,x-csrf-token")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            if (origin.contains("localhost")) {
                assertThat(preflight.statusCode()).isEqualTo(200);
                assertThat(preflight.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                    .contains("idempotency-key");
            } else {
                assertThat(preflight.statusCode()).isEqualTo(403);
            }
        }
    }

    String code(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("code").asText();
    }

    class Browser {
        final String code;
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        Browser(String code) { this.code = code; }
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        /** POST with a fresh idempotency key. */
        HttpResponse<String> post(String path, Object body) throws Exception { return send(path, body, UUID.randomUUID().toString()); }
        HttpResponse<String> post(String path, Object body, String key) throws Exception { return send(path, body, key); }
        HttpResponse<String> send(String path, Object body, String key) throws Exception {
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
