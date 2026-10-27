package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=configuration-bootstrap-admin", "app.admin-password=temporary configuration bootstrap"})
class ProductConfigurationHttpTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "optiplant_test_runtime");
        registry.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;


    // RF-0001-CA-01 and RF-0001-CA-02
    @Test
    void administratorChangesOnlyTheExplicitMinimumOfEachProductBranch() throws Exception {
        createAccount("min-admin", "ADMIN", null);
        UUID north = createBranch("MIN-NORTH", true);
        UUID south = createBranch("MIN-SOUTH", true);
        UUID absent = createBranch("MIN-ABSENT", true);
        long actor = createAccount("min-operator", "OPERATOR", north);
        createAccount("min-manager", "MANAGER", north);
        Browser admin = authenticated("min-admin");
        UUID whole = createProduct(admin, product("SKU-MIN-WHOLE", "Entero", null, "unidad", false,
            List.of(enable(north, "5"), enable(south, "3"))));
        UUID fraction = createProduct(admin, product("SKU-MIN-KG", "Granel", null, "kg", true, List.of(enable(north, "1"))));
        long productId = productId(whole);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,4,40,10,CURRENT_TIMESTAMP)", north, productId);
        jdbc.update("INSERT INTO purchases_supplier(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), "MIN-SUP", "Proveedor");
        jdbc.update("INSERT INTO purchases_order(external_id,code,branch_id,supplier_id,supplier_name_snapshot,status,created_by,"
            + "payment_term_days) VALUES (?,'MIN-ORDER',(SELECT id FROM identity_branch WHERE external_id=?),"
            + "(SELECT id FROM purchases_supplier WHERE code='MIN-SUP'),'Proveedor','DRAFT',?,0)", UUID.randomUUID(), north, actor);
        String operations = operationsDigest();
        long orders = jdbc.queryForObject("SELECT count(*) FROM purchases_order", Long.class);

        HttpResponse<String> changed = minimum(admin, whole, north, "0");
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(minimumOf(json.readTree(changed.body()), "MIN-NORTH")).isEqualTo("0.000");
        assertThat(minimumOf(json.readTree(admin.get("/api/v1/products/" + whole).body()), "MIN-NORTH")).isEqualTo("0.000");
        assertThat(minimumOf(json.readTree(admin.get("/api/v1/products/" + whole).body()), "MIN-SOUTH")).isEqualTo("3.000");
        assertThat(minimum(admin, whole, north, "12").statusCode()).isEqualTo(200);
        assertThat(minimumOf(json.readTree(admin.get("/api/v1/products/" + whole).body()), "MIN-NORTH")).isEqualTo("12.000");
        assertThat(minimum(admin, fraction, north, "2.5").statusCode()).isEqualTo(200);
        assertThat(minimumOf(json.readTree(admin.get("/api/v1/products/" + fraction).body()), "MIN-NORTH")).isEqualTo("2.500");
        // Stock, cost and documents keep their values; no purchase is created.
        assertThat(operationsDigest()).isEqualTo(operations);
        // RF-0019-CA-02: restock alerts follow the new minimum (stock 4: resolved above 0, reopened at 12); history is kept.
        assertThat(alerts(whole, north)).containsExactly("RESOLVED 5 0", "ACTIVE 12 4");
        assertThat(alerts(whole, south)).containsExactly("ACTIVE 3 0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchases_order", Long.class)).isEqualTo(orders);

        String before = catalogDigest();
        for (String invalid : new String[] {"1.5", "-1", "1.2345", "", "abc"}) {
            assertProblem(minimum(admin, whole, north, invalid), 400, "VALIDATION_FAILED");
        }
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + whole + "/branches/" + north + "/minimum",
            Map.of("minimumQuantity", "1", "enabled", false)), 400, "VALIDATION_FAILED");
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + whole + "/branches/" + north + "/minimum", Map.of("minimumQuantity", 1)),
            400, "VALIDATION_FAILED");
        assertProblem(minimum(admin, whole, absent, "1"), 409, "PRODUCT_NOT_ENABLED");
        assertProblem(minimum(admin, whole, UUID.randomUUID(), "1"), 404, "BRANCH_NOT_FOUND");
        assertProblem(minimum(admin, UUID.randomUUID(), north, "1"), 404, "PRODUCT_NOT_FOUND");
        for (String code : new String[] {"min-manager", "min-operator"}) {
            assertProblem(minimum(authenticated(code), whole, north, "1"), 403, "ROLE_FORBIDDEN");
        }
        assertThat(catalogDigest()).isEqualTo(before);
    }

    // RF-0013-CA-01, RF-0013-CA-02 and RF-0013-CA-03
    @Test
    void administratorManagesCommercialUnitsWithExactConversionsAndHistoricalSnapshots() throws Exception {
        createAccount("unit-admin", "ADMIN", null);
        UUID branch = createBranch("UNIT-BRANCH", true);
        long actor = createAccount("unit-operator", "OPERATOR", branch);
        createAccount("unit-manager", "MANAGER", branch);
        Browser admin = authenticated("unit-admin");
        UUID product = createProduct(admin, product("SKU-UNIT", "Maceta", null, "unidad", false, List.of(enable(branch, "1"))));

        HttpResponse<String> created = unit(admin, product, Map.of("name", "caja", "factor", "12", "divisible", false));
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode units = json.readTree(admin.get("/api/v1/products/" + product).body()).path("units");
        assertThat(units).hasSize(2);
        assertThat(units.get(0).path("name").asText()).isEqualTo("unidad");
        assertThat(units.get(0).path("base").asBoolean()).isTrue();
        assertThat(units.get(0).path("factor").asText()).isEqualTo("1.000");
        assertThat(units.get(1).path("name").asText()).isEqualTo("caja");
        assertThat(units.get(1).path("base").asBoolean()).isFalse();
        assertThat(units.get(1).path("factor").asText()).isEqualTo("12.000");

        Browser operator = authenticated("unit-operator");
        JsonNode box = json.readTree(convert(operator, product, "caja", "1").body());
        assertThat(box.path("baseQuantity").asText()).isEqualTo("12.000");
        assertThat(box.path("factor").asText()).isEqualTo("12.000");
        assertThat(box.path("baseUnitName").asText()).isEqualTo("unidad");
        assertProblem(convert(operator, product, "caja", "1.5"), 400, "VALIDATION_FAILED");
        assertProblem(convert(operator, product, "caja", "0"), 400, "VALIDATION_FAILED");
        assertProblem(convert(operator, product, "pallet", "1"), 404, "PRODUCT_UNIT_NOT_FOUND");

        String before = catalogDigest();
        assertProblem(unit(admin, product, Map.of("name", "Caja", "factor", "6", "divisible", false)), 409, "PRODUCT_UNIT_EXISTS");
        assertProblem(unit(admin, product, Map.of("name", "UNIDAD", "factor", "6", "divisible", false)), 409, "PRODUCT_UNIT_EXISTS");
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
            Map.of("name", "media", "factor", "0.5", "divisible", false),
            Map.of("name", "suelta", "factor", "2", "divisible", true),
            Map.of("name", "cero", "factor", "0", "divisible", false),
            Map.of("name", " espacio", "factor", "2", "divisible", false),
            Map.of("name", "extra", "factor", "2", "divisible", false, "base", true),
            Map.of("name", "numero", "factor", 2, "divisible", false))) {
            assertProblem(unit(admin, product, invalid), 400, "VALIDATION_FAILED");
        }
        assertProblem(unitUpdate(admin, product, Map.of("name", "unidad", "factor", "2", "active", true)), 409, "PRODUCT_BASE_UNIT_FIXED");
        assertProblem(unitUpdate(admin, product, Map.of("name", "pallet", "factor", "2", "active", true)), 404, "PRODUCT_UNIT_NOT_FOUND");
        for (String code : new String[] {"unit-manager", "unit-operator"}) {
            Browser local = authenticated(code);
            assertProblem(unit(local, product, Map.of("name", "pack", "factor", "6", "divisible", false)), 403, "ROLE_FORBIDDEN");
            assertProblem(unitUpdate(local, product, Map.of("name", "caja", "factor", "6", "active", true)), 403, "ROLE_FORBIDDEN");
        }
        assertThat(catalogDigest()).isEqualTo(before);

        // A confirmed document keeps its unit, factor and quantities after the administrator changes the conversion.
        long productId = productId(product);
        long boxId = jdbc.queryForObject("SELECT id FROM catalog_product_unit WHERE product_id=? AND name='caja'", Long.class, productId);
        jdbc.update("INSERT INTO purchases_supplier(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), "UNIT-SUP", "Proveedor");
        long orderId = jdbc.queryForObject("INSERT INTO purchases_order(external_id,code,branch_id,supplier_id,supplier_name_snapshot,"
                + "status,created_by,payment_term_days) VALUES (?,'UNIT-ORDER',(SELECT id FROM identity_branch WHERE external_id=?),"
                + "(SELECT id FROM purchases_supplier WHERE code='UNIT-SUP'),'Proveedor','PENDING',?,0) RETURNING id",
            Long.class, UUID.randomUUID(), branch, actor);
        jdbc.update("INSERT INTO purchases_line(order_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,"
            + "unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,net_amount) "
            + "VALUES (?,1,?,?,'SKU-UNIT','Maceta','caja',12,2,24,30,0,60)", orderId, productId, boxId);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,24,60,2.5,CURRENT_TIMESTAMP)", branch, productId);
        String document = jdbc.queryForObject("SELECT row_to_json(l)::text FROM purchases_line l WHERE order_id=?", String.class, orderId);
        String balance = jdbc.queryForObject("SELECT row_to_json(b)::text FROM inventory_balance b WHERE product_id=?", String.class, productId);

        assertThat(unitUpdate(admin, product, Map.of("name", "caja", "factor", "10", "active", true)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT row_to_json(l)::text FROM purchases_line l WHERE order_id=?", String.class, orderId))
            .isEqualTo(document);
        assertThat(jdbc.queryForObject("SELECT row_to_json(b)::text FROM inventory_balance b WHERE product_id=?", String.class, productId))
            .isEqualTo(balance);
        assertThat(json.readTree(convert(operator, product, "caja", "1").body()).path("baseQuantity").asText()).isEqualTo("10.000");
        // The used base unit cannot change.
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/update", update("SKU-UNIT", "Maceta", null, "pieza", false)),
            409, "PRODUCT_IDENTITY_PROTECTED");

        assertThat(unitUpdate(admin, product, Map.of("name", "caja", "factor", "10", "active", false)).statusCode()).isEqualTo(200);
        assertProblem(convert(operator, product, "caja", "1"), 404, "PRODUCT_UNIT_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_product_unit WHERE product_id=?", Long.class, productId)).isEqualTo(2);
    }

    // RF-0013-CA-02: three base decimals, no implicit rounding
    @Test
    void divisibleUnitsRejectBaseEquivalentsBeyondThreeDecimals() throws Exception {
        createAccount("decimal-admin", "ADMIN", null);
        Browser admin = authenticated("decimal-admin");
        UUID product = createProduct(admin, product("SKU-KG", "Sustrato", null, "kg", true, List.of()));
        assertThat(unit(admin, product, Map.of("name", "gramo", "factor", "0.001", "divisible", true)).statusCode()).isEqualTo(200);
        assertThat(unit(admin, product, Map.of("name", "saco", "factor", "25.5", "divisible", true)).statusCode()).isEqualTo(200);
        assertProblem(unit(admin, product, Map.of("name", "pizca", "factor", "0.0001", "divisible", true)), 400, "VALIDATION_FAILED");

        assertThat(json.readTree(convert(admin, product, "gramo", "250").body()).path("baseQuantity").asText()).isEqualTo("0.250");
        assertThat(json.readTree(convert(admin, product, "saco", "0.5").body()).path("baseQuantity").asText()).isEqualTo("12.750");
        assertProblem(convert(admin, product, "gramo", "0.5"), 400, "VALIDATION_FAILED");
        assertProblem(convert(admin, product, "saco", "0.123"), 400, "VALIDATION_FAILED");

        // Divisible commercial units keep the base unit divisible.
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/update", update("SKU-KG", "Sustrato", null, "kg", false)),
            409, "PRODUCT_UNITS_INCOMPATIBLE");
    }

    // RF-0012-CA-01 and RF-0012-CA-02
    @Test
    void localProfilesSeeOnlyProductsEnabledInTheirOwnBranchWithPhysicalAvailableAndTransit() throws Exception {
        createAccount("local-admin", "ADMIN", null);
        UUID own = createBranch("LOC-OWN", true);
        UUID other = createBranch("LOC-OTHER", true);
        long actor = createAccount("local-operator", "OPERATOR", own);
        createAccount("local-manager", "MANAGER", own);
        createAccount("other-operator", "OPERATOR", other);
        Browser admin = authenticated("local-admin");
        UUID empty = createProduct(admin, product("SKU-LOC-EMPTY", "Sin stock", null, "unidad", false, List.of(enable(own, "0"))));
        UUID stocked = createProduct(admin, product("SKU-LOC-STOCK", "Con stock", null, "kg", true,
            List.of(enable(own, "2.5"), enable(other, "1"))));
        createProduct(admin, product("SKU-LOC-FOREIGN", "Ajeno", null, "unidad", false, List.of(enable(other, "1"))));
        unit(admin, stocked, Map.of("name", "saco", "factor", "25", "divisible", false));
        long stockedId = productId(stocked);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,7.25,70,10,CURRENT_TIMESTAMP)", own, stockedId);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,40,400,10,CURRENT_TIMESTAMP)", other, stockedId);
        transit(other, own, stockedId, actor, "3.5");

        for (String code : new String[] {"local-operator", "local-manager"}) {
            HttpResponse<String> response = authenticated(code).get("/api/v1/products/local");
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json.readTree(response.body());
            assertThat(body.path("branchExternalId").asText()).isEqualTo(own.toString());
            assertThat(body.path("branchCode").asText()).isEqualTo("LOC-OWN");
            assertThat(body.path("branchActive").asBoolean()).isTrue();
            JsonNode items = body.path("items");
            assertThat(items).hasSize(2);
            assertThat(items.get(0).path("productExternalId").asText()).isEqualTo(empty.toString());
            assertThat(items.get(0).path("physicalQuantity").asText()).isEqualTo("0.000");
            assertThat(items.get(0).path("availableQuantity").asText()).isEqualTo("0.000");
            assertThat(items.get(0).path("inTransitQuantity").asText()).isEqualTo("0.000");
            assertThat(items.get(0).path("minimumQuantity").asText()).isEqualTo("0.000");
            assertThat(items.get(1).path("sku").asText()).isEqualTo("SKU-LOC-STOCK");
            assertThat(items.get(1).path("physicalQuantity").asText()).isEqualTo("7.250");
            assertThat(items.get(1).path("availableQuantity").asText()).isEqualTo("7.250");
            assertThat(items.get(1).path("inTransitQuantity").asText()).isEqualTo("3.500");
            assertThat(items.get(1).path("units")).hasSize(2);
            assertThat(response.body()).doesNotContain("SKU-LOC-FOREIGN", "inventoryValue", "averageBaseCost", "\"id\"");
        }
        JsonNode foreign = json.readTree(authenticated("other-operator").get("/api/v1/products/local").body()).path("items");
        assertThat(foreign).hasSize(2);
        assertThat(foreign.get(1).path("physicalQuantity").asText()).isEqualTo("40.000");
        assertThat(foreign.get(1).path("inTransitQuantity").asText()).isEqualTo("0.000");
        assertProblem(admin.get("/api/v1/products/local"), 403, "ROLE_FORBIDDEN");

        // A deactivated product leaves the local catalog of every branch.
        jdbc.update("UPDATE catalog_branch_product SET enabled=FALSE,minimum_qty=NULL WHERE product_id=?", productId(empty));
        assertThat(json.readTree(authenticated("local-operator").get("/api/v1/products/local").body()).path("items")).hasSize(1);
    }

    private void transit(UUID origin, UUID destination, long productId, long actor, String quantity) {
        long unitId = jdbc.queryForObject("SELECT id FROM catalog_product_unit WHERE product_id=? AND is_base", Long.class, productId);
        long transferId = jdbc.queryForObject("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,"
                + "requested_by,requested_at,status) VALUES (?,'LOC-TRANSFER',(SELECT id FROM identity_branch WHERE external_id=?),"
                + "(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,'IN_TRANSIT') RETURNING id",
            Long.class, UUID.randomUUID(), origin, destination, actor);
        long lineId = jdbc.queryForObject("INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,"
                + "product_name_snapshot,unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty) "
                + "VALUES (?,1,?,?,'SKU','Producto','kg',1,?::numeric,?::numeric) RETURNING id", Long.class, transferId, productId, unitId,
            quantity, quantity);
        long routeId = jdbc.queryForObject("INSERT INTO logistics_route(external_id,code,origin_branch_id,destination_branch_id,priority) "
                + "VALUES (?,'LOC-ROUTE',(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), origin, destination);
        long dispatchId = jdbc.queryForObject("INSERT INTO logistics_dispatch(external_id,transfer_id,route_id,carrier_text,dispatched_at,"
                + "estimated_arrival_at,dispatched_by,route_priority_snapshot) VALUES (?,?,?,'Transportista',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), transferId, routeId, actor);
        long dispatchLineId = jdbc.queryForObject("INSERT INTO logistics_dispatch_line(dispatch_id,transfer_line_id,dispatched_commercial_qty,"
            + "dispatched_base_qty,frozen_base_cost,base_value) VALUES (?,?,?::numeric,?::numeric,1,?::numeric) RETURNING id", Long.class,
            dispatchId, lineId, quantity, quantity, quantity);
        jdbc.update("INSERT INTO inventory_transit(dispatch_line_id,pending_base_qty,pending_base_value) VALUES (?,?::numeric,1)",
            dispatchLineId, quantity);
    }

    private UUID createProduct(Browser admin, Map<String, Object> body) throws Exception {
        admin.csrf();
        HttpResponse<String> response = admin.post("/api/v1/products", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("externalId").asText());
    }

    private HttpResponse<String> minimum(Browser browser, UUID product, UUID branch, String value) throws Exception {
        browser.csrf();
        return browser.post("/api/v1/products/" + product + "/branches/" + branch + "/minimum", Map.of("minimumQuantity", value));
    }

    private HttpResponse<String> unit(Browser browser, UUID product, Map<String, Object> body) throws Exception {
        browser.csrf();
        return browser.post("/api/v1/products/" + product + "/units", body);
    }

    private HttpResponse<String> unitUpdate(Browser browser, UUID product, Map<String, Object> body) throws Exception {
        browser.csrf();
        return browser.post("/api/v1/products/" + product + "/units/update", body);
    }

    private HttpResponse<String> convert(Browser browser, UUID product, String unit, String quantity) throws Exception {
        return browser.get("/api/v1/products/" + product + "/units/convert?unit=" + URLEncoder.encode(unit, StandardCharsets.UTF_8)
            + "&quantity=" + URLEncoder.encode(quantity, StandardCharsets.UTF_8));
    }

    private static String minimumOf(JsonNode product, String branchCode) {
        for (JsonNode branch : product.path("branches")) {
            if (branchCode.equals(branch.path("branchCode").asText())) return branch.path("minimumQuantity").asText(null);
        }
        throw new AssertionError("Branch not listed: " + branchCode);
    }

    /** Restock episodes of a product-branch, oldest first: "status minimumSnapshot quantitySnapshot". */
    private List<String> alerts(UUID product, UUID branch) {
        return jdbc.queryForList("SELECT CASE WHEN a.resolved_at IS NULL THEN 'ACTIVE' ELSE 'RESOLVED' END||' '||"
            + "trim_scale(a.minimum_snapshot)||' '||trim_scale(a.quantity_snapshot) FROM inventory_reorder_alert a "
            + "JOIN catalog_branch_product bp ON bp.id=a.branch_product_id JOIN identity_branch b ON b.id=bp.branch_id "
            + "JOIN catalog_product p ON p.id=bp.product_id WHERE p.external_id=? AND b.external_id=? ORDER BY a.id",
            String.class, product, branch);
    }

    private String operationsDigest() {
        StringBuilder digest = new StringBuilder();
        for (String table : new String[]{"inventory_balance", "inventory_movement", "inventory_operation", "purchases_order",
            "purchases_line"}) {
            digest.append(jdbc.queryForObject("SELECT md5(coalesce(string_agg(row_to_json(t)::text, '' ORDER BY id),'')) FROM "
                + table + " t", String.class));
        }
        return digest.toString();
    }

    private static Map<String, Object> product(String sku, String name, String description, String baseUnitName,
        boolean divisible, List<Map<String, Object>> branches) {
        Map<String, Object> body = update(sku, name, description, baseUnitName, divisible);
        body.put("branches", branches);
        return body;
    }

    private static Map<String, Object> update(String sku, String name, String description, String baseUnitName, boolean divisible) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("description", description);
        body.put("baseUnitName", baseUnitName);
        body.put("baseDivisible", divisible);
        return body;
    }

    private static Map<String, Object> enable(UUID branch, String minimum) {
        return Map.of("branchExternalId", branch.toString(), "minimumQuantity", minimum);
    }

    private String catalogDigest() {
        StringBuilder digest = new StringBuilder();
        for (String table : new String[]{"catalog_product", "catalog_product_unit", "catalog_branch_product", "inventory_balance",
            "inventory_reorder_alert"}) {
            digest.append(jdbc.queryForObject("SELECT md5(coalesce(string_agg(row_to_json(t)::text, '' ORDER BY id),'')) FROM "
                + table + " t", String.class));
        }
        return digest.toString();
    }

    private long productId(UUID externalId) {
        return jdbc.queryForObject("SELECT id FROM catalog_product WHERE external_id=?", Long.class, externalId);
    }

    private UUID createBranch(String code, boolean active) {
        UUID externalId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name,active) VALUES (?,?,?,?)", externalId, code, code, active);
        return externalId;
    }

    private long createAccount(String code, String role, UUID branch) {
        UUID externalId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
                + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)", externalId, code,
            "Cuenta de prueba", passwords.encode("fixture individual password"), role, branch);
        return jdbc.queryForObject("SELECT id FROM identity_user_account WHERE external_id=?", Long.class, externalId);
    }

    private Browser authenticated(String code) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", code, "password", "fixture individual password"))
            .statusCode()).isEqualTo(200);
        return browser;
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
    }

    class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header;

        void csrf() throws Exception {
            HttpResponse<String> response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json.readTree(response.body());
            token = body.path("token").asText();
            header = body.path("headerName").asText();
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header(header, token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> delete(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header(header, token).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        }

        URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    }
}
