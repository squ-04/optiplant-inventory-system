package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.URI;
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
    properties = {"app.admin-code=product-bootstrap-admin", "app.admin-password=temporary product bootstrap"})
class ProductHttpTest {
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

    @Test
    void administratorCreatesProductWithBaseUnitAndExplicitBranchEnablementWithoutStock() throws Exception {
        createAccount("product-create-admin", "ADMIN", null);
        UUID north = createBranch("PC-NORTH", true);
        UUID south = createBranch("PC-SOUTH", true);
        Browser admin = authenticated("product-create-admin");
        admin.csrf();
        HttpResponse<String> created = admin.post("/api/v1/products", product("SKU-CREATE", "Helecho", "Maceta 12 cm",
            "unidad", false, List.of(enable(north, "5"), enable(south, "0"))));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        UUID externalId = UUID.fromString(body.path("externalId").asText());
        assertThat(externalId.version()).isEqualTo(4);
        assertThat(body.has("id")).isFalse();
        assertThat(body.path("sku").asText()).isEqualTo("SKU-CREATE");
        assertThat(body.path("description").asText()).isEqualTo("Maceta 12 cm");
        assertThat(body.path("baseUnitName").asText()).isEqualTo("unidad");
        assertThat(body.path("baseDivisible").asBoolean()).isFalse();
        assertThat(body.path("active").asBoolean()).isTrue();
        assertThat(body.path("inUse").asBoolean()).isFalse();
        assertThat(body.path("branches")).hasSize(2);
        assertThat(body.path("branches").toString()).contains("\"minimumQuantity\":\"5.000\"", "\"minimumQuantity\":\"0.000\"");

        long productId = productId(externalId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_product_unit WHERE product_id=? AND is_base AND factor=1 "
            + "AND name='unidad' AND NOT divisible", Long.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_branch_product WHERE product_id=? AND enabled", Long.class,
            productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_balance WHERE product_id=?", Long.class, productId)).isZero();

        JsonNode detail = json.readTree(admin.get("/api/v1/products/" + externalId).body());
        assertThat(detail.path("name").asText()).isEqualTo("Helecho");
        assertThat(admin.get("/api/v1/products").body()).contains("SKU-CREATE");
    }

    @Test
    void rejectsDuplicateSkuAndInvalidPayloadsWithoutChangingTheCatalog() throws Exception {
        createAccount("product-validation-admin", "ADMIN", null);
        UUID branch = createBranch("PV-BRANCH", true);
        UUID inactive = createBranch("PV-INACTIVE", false);
        Browser admin = authenticated("product-validation-admin");
        admin.csrf();
        assertThat(admin.post("/api/v1/products", product("SKU-DUP", "Original", null, "unidad", false, List.of()))
            .statusCode()).isEqualTo(201);
        String before = catalogDigest();

        admin.csrf();
        assertProblem(admin.post("/api/v1/products", product("SKU-DUP", "Copia", null, "unidad", false, List.of())), 409,
            "PRODUCT_SKU_EXISTS");
        Map<String, Object> extra = product("SKU-EXTRA", "Extra", null, "unidad", false, List.of());
        extra.put("active", false);
        for (Map<String, Object> invalid : List.of(product(" SKU-SPACE", "Nombre", null, "unidad", false, List.of()),
            product("SKU-BLANK", " ", null, "unidad", false, List.of()),
            product("SKU-NO-UNIT", "Nombre", null, "", false, List.of()),
            product("SKU-FRACTION", "Nombre", null, "unidad", false, List.of(enable(branch, "1.5"))),
            product("SKU-PRECISION", "Nombre", null, "kg", true, List.of(enable(branch, "1.2345"))),
            product("SKU-NEGATIVE", "Nombre", null, "kg", true, List.of(enable(branch, "-1"))),
            product("SKU-REPEATED", "Nombre", null, "kg", true, List.of(enable(branch, "1"), enable(branch, "2"))),
            extra)) {
            admin.csrf();
            assertProblem(admin.post("/api/v1/products", invalid), 400, "VALIDATION_FAILED");
        }
        admin.csrf();
        assertProblem(admin.post("/api/v1/products", product("SKU-INACTIVE-BRANCH", "Nombre", null, "kg", true,
            List.of(enable(inactive, "1")))), 409, "BRANCH_INACTIVE");
        admin.csrf();
        assertProblem(admin.post("/api/v1/products", product("SKU-UNKNOWN-BRANCH", "Nombre", null, "kg", true,
            List.of(enable(UUID.randomUUID(), "1")))), 404, "BRANCH_NOT_FOUND");
        assertThat(catalogDigest()).isEqualTo(before);
    }

    @Test
    void localProfilesQueryProductsButCannotMaintainThem() throws Exception {
        UUID branch = createBranch("PL-OWN", true);
        UUID other = createBranch("PL-OTHER", true);
        createAccount("product-local-admin", "ADMIN", null);
        createAccount("product-manager", "MANAGER", branch);
        createAccount("product-operator", "OPERATOR", branch);
        Browser admin = authenticated("product-local-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-LOCAL", "Local", null, "unidad", false, List.of(enable(branch, "1"), enable(other, "9")))).body())
            .path("externalId").asText());
        String before = catalogDigest();

        for (String code : new String[] {"product-manager", "product-operator"}) {
            Browser local = authenticated(code);
            assertThat(local.get("/api/v1/products").body()).contains("SKU-LOCAL");
            JsonNode detail = json.readTree(local.get("/api/v1/products/" + product).body());
            assertThat(detail.path("sku").asText()).isEqualTo("SKU-LOCAL");
            assertThat(detail.path("branches").isNull()).isTrue();
            local.csrf();
            assertProblem(local.post("/api/v1/products", product("SKU-FORBIDDEN", "No", null, "u", false, List.of())), 403,
                "ROLE_FORBIDDEN");
            local.csrf();
            assertProblem(local.post("/api/v1/products/" + product + "/update",
                update("SKU-LOCAL", "Cambio", null, "unidad", false)), 403, "ROLE_FORBIDDEN");
            local.csrf();
            assertProblem(local.post("/api/v1/products/" + product + "/branches", enable(other, "1")), 403, "ROLE_FORBIDDEN");
            local.csrf();
            assertProblem(local.post("/api/v1/products/" + product + "/deactivate", Map.of()), 403, "ROLE_FORBIDDEN");
            local.csrf();
            assertProblem(local.post("/api/v1/products/" + product + "/reactivate", Map.of()), 403, "ROLE_FORBIDDEN");
        }
        assertThat(catalogDigest()).isEqualTo(before);
        assertProblem(admin.get("/api/v1/products/" + UUID.randomUUID()), 404, "PRODUCT_NOT_FOUND");
        assertThat(new Browser().get("/api/v1/products").statusCode()).isEqualTo(401);
    }

    @Test
    void updateChangesCommercialAttributesAndProtectsIdentityAfterUse() throws Exception {
        createAccount("product-update-admin", "ADMIN", null);
        UUID branch = createBranch("PU-BRANCH", true);
        Browser admin = authenticated("product-update-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-UPDATE", "Antes", null, "unidad", false, List.of())).body()).path("externalId").asText());

        admin.csrf();
        HttpResponse<String> unused = admin.post("/api/v1/products/" + product + "/update",
            update("SKU-RENAMED", "Durante", "Nueva descripción", "kg", true));
        assertThat(unused.statusCode()).isEqualTo(200);
        assertThat(json.readTree(unused.body()).path("baseUnitName").asText()).isEqualTo("kg");
        long productId = productId(product);
        assertThat(jdbc.queryForObject("SELECT name || ':' || divisible FROM catalog_product_unit WHERE product_id=? AND is_base",
            String.class, productId)).isEqualTo("kg:true");

        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,0,0,0,CURRENT_TIMESTAMP)", branch, productId);
        admin.csrf();
        HttpResponse<String> commercial = admin.post("/api/v1/products/" + product + "/update",
            update("SKU-RENAMED", "Después", null, "kg", true));
        assertThat(commercial.statusCode()).isEqualTo(200);
        JsonNode detail = json.readTree(admin.get("/api/v1/products/" + product).body());
        assertThat(detail.path("name").asText()).isEqualTo("Después");
        assertThat(detail.path("description").isNull()).isTrue();
        assertThat(detail.path("inUse").asBoolean()).isTrue();
        assertThat(detail.path("externalId").asText()).isEqualTo(product.toString());

        String before = catalogDigest();
        for (Map<String, Object> protectedChange : List.of(update("SKU-OTHER", "Después", null, "kg", true),
            update("SKU-RENAMED", "Después", null, "g", true), update("SKU-RENAMED", "Después", null, "kg", false))) {
            admin.csrf();
            assertProblem(admin.post("/api/v1/products/" + product + "/update", protectedChange), 409,
                "PRODUCT_IDENTITY_PROTECTED");
        }
        Map<String, Object> identity = update("SKU-RENAMED", "Después", null, "kg", true);
        identity.put("externalId", UUID.randomUUID().toString());
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/update", identity), 400, "VALIDATION_FAILED");
        assertThat(catalogDigest()).isEqualTo(before);
    }

    @Test
    void enablingInAnotherBranchKeepsIdentityAndCreatesNoStock() throws Exception {
        createAccount("product-enable-admin", "ADMIN", null);
        UUID first = createBranch("PE-FIRST", true);
        UUID second = createBranch("PE-SECOND", true);
        Browser admin = authenticated("product-enable-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-ENABLE", "Habilitable", null, "kg", true, List.of(enable(first, "1")))).body())
            .path("externalId").asText());
        admin.csrf();
        HttpResponse<String> enabled = admin.post("/api/v1/products/" + product + "/branches", enable(second, "2.5"));
        assertThat(enabled.statusCode()).isEqualTo(200);
        assertThat(json.readTree(enabled.body()).path("externalId").asText()).isEqualTo(product.toString());
        assertThat(json.readTree(enabled.body()).path("branches")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_balance WHERE product_id=?", Long.class,
            productId(product))).isZero();
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/branches", enable(second, "3")), 409,
            "PRODUCT_ALREADY_ENABLED");
    }

    @Test
    void deactivationIsLogicalOnlyAndRejectedWhileStockOrPendingOperationsExist() throws Exception {
        createAccount("product-deactivate-admin", "ADMIN", null);
        UUID branch = createBranch("PD-BRANCH", true);
        UUID other = createBranch("PD-OTHER", true);
        long actor = createAccount("product-deactivate-operator", "OPERATOR", branch);
        Browser admin = authenticated("product-deactivate-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-DEACTIVATE", "Baja", null, "unidad", false, List.of(enable(branch, "4"), enable(other, "1")))).body())
            .path("externalId").asText());
        long productId = productId(product);
        long unitId = jdbc.queryForObject("SELECT id FROM catalog_product_unit WHERE product_id=? AND is_base", Long.class, productId);

        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),?,1,1,1,CURRENT_TIMESTAMP)", other, productId);
        assertBlocked(admin, product);
        jdbc.update("UPDATE inventory_balance SET physical_qty=0,inventory_value=0 WHERE product_id=?", productId);

        jdbc.update("INSERT INTO purchases_supplier(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), "PD-SUP", "Proveedor");
        for (String status : new String[] {"DRAFT", "PENDING"}) {
            long orderId = jdbc.queryForObject("INSERT INTO purchases_order(external_id,code,branch_id,supplier_id,supplier_name_snapshot,"
                    + "status,created_by,payment_term_days) VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),"
                    + "(SELECT id FROM purchases_supplier WHERE code='PD-SUP'),'Proveedor',?,?,0) RETURNING id",
                Long.class, UUID.randomUUID(), "PD-ORDER-" + status, branch, status, actor);
            jdbc.update("INSERT INTO purchases_line(order_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,"
                    + "unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,net_amount) "
                    + "VALUES (?,1,?,?,'SKU-DEACTIVATE','Baja','unidad',1,1,1,1,0,1)",
                orderId, productId, unitId);
            assertBlocked(admin, product);
            jdbc.update("UPDATE purchases_order SET status='CANCELLED' WHERE id=?", orderId);
        }

        for (String status : new String[] {"REQUESTED", "PREPARING", "APPROVED", "IN_TRANSIT"}) {
            long transferId = jdbc.queryForObject("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,"
                    + "requested_by,requested_at,status) VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),"
                    + "(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,?) RETURNING id",
                Long.class, UUID.randomUUID(), "PD-TRANSFER-" + status, other, branch, actor, status);
            jdbc.update("INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,"
                    + "unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty) VALUES (?,1,?,?,'SKU-DEACTIVATE','Baja','unidad',1,1,1)",
                transferId, productId, unitId);
            assertBlocked(admin, product);
            jdbc.update("UPDATE transfers_transfer SET status='CANCELLED' WHERE id=?", transferId);
        }

        long transferId = jdbc.queryForObject("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,"
                + "requested_by,requested_at,status) VALUES (?,'PD-TRANSFER-DISCREPANCY',(SELECT id FROM identity_branch WHERE external_id=?),"
                + "(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,'RECEIVED_DISCREPANCY') RETURNING id",
            Long.class, UUID.randomUUID(), other, branch, actor);
        long lineId = jdbc.queryForObject("INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,"
                + "product_name_snapshot,unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty) "
                + "VALUES (?,1,?,?,'SKU-DEACTIVATE','Baja','unidad',1,1,1) RETURNING id", Long.class, transferId, productId, unitId);
        long routeId = jdbc.queryForObject("INSERT INTO logistics_route(external_id,code,origin_branch_id,destination_branch_id,priority) "
                + "VALUES (?,'PD-ROUTE',(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), other, branch);
        long dispatchId = jdbc.queryForObject("INSERT INTO logistics_dispatch(external_id,transfer_id,route_id,carrier_text,dispatched_at,"
                + "estimated_arrival_at,dispatched_by,route_priority_snapshot) VALUES (?,?,?,'Transportista',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), transferId, routeId, actor);
        long dispatchLineId = jdbc.queryForObject("INSERT INTO logistics_dispatch_line(dispatch_id,transfer_line_id,dispatched_commercial_qty,"
            + "dispatched_base_qty,frozen_base_cost,base_value) VALUES (?,?,1,1,1,1) RETURNING id", Long.class, dispatchId, lineId);
        jdbc.update("INSERT INTO inventory_transit(dispatch_line_id,pending_base_qty,pending_base_value) VALUES (?,1,1)", dispatchLineId);
        assertBlocked(admin, product);
        jdbc.update("UPDATE inventory_transit SET pending_base_qty=0,pending_base_value=0 WHERE dispatch_line_id=?", dispatchLineId);
        long arrivalId = jdbc.queryForObject("INSERT INTO logistics_arrival(external_id,dispatch_id,arrived_at,confirmed_by) "
            + "VALUES (?,?,CURRENT_TIMESTAMP,?) RETURNING id", Long.class, UUID.randomUUID(), dispatchId, actor);
        long arrivalLineId = jdbc.queryForObject("INSERT INTO logistics_arrival_line(arrival_id,dispatch_line_id,received_commercial_qty,"
            + "received_base_qty) VALUES (?,?,0,0) RETURNING id", Long.class, arrivalId, dispatchLineId);
        jdbc.update("INSERT INTO logistics_discrepancy(external_id,arrival_id,opened_at,status) VALUES (?,?,CURRENT_TIMESTAMP,'OPEN')",
            UUID.randomUUID(), arrivalId);
        jdbc.update("INSERT INTO logistics_discrepancy_line(discrepancy_id,arrival_line_id,missing_base_qty) "
            + "VALUES ((SELECT id FROM logistics_discrepancy WHERE arrival_id=?),?,1)", arrivalId, arrivalLineId);
        assertBlocked(admin, product);
        jdbc.update("UPDATE logistics_discrepancy SET status='RESOLVED',treatment='CLAIM',resolved_by=?,resolved_at=CURRENT_TIMESTAMP "
            + "WHERE arrival_id=?", actor, arrivalId);

        long branchProduct = jdbc.queryForObject("SELECT bp.id FROM catalog_branch_product bp JOIN identity_branch b ON b.id=bp.branch_id "
            + "WHERE b.external_id=? AND bp.product_id=?", Long.class, branch, productId);
        // The enablement at zero stock already opened its restock alert (RF-0020); deactivation must close it.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reorder_alert WHERE branch_product_id=? AND resolved_at IS NULL",
            Long.class, branchProduct)).isEqualTo(1);

        admin.csrf();
        HttpResponse<String> deactivated = admin.post("/api/v1/products/" + product + "/deactivate", Map.of());
        assertThat(deactivated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(deactivated.body()).path("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT active FROM catalog_product WHERE id=?", Boolean.class, productId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_branch_product WHERE product_id=? AND (enabled OR minimum_qty IS NOT NULL)",
            Long.class, productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_branch_product WHERE product_id=?", Long.class, productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reorder_alert WHERE branch_product_id=? AND resolved_at IS NULL",
            Long.class, branchProduct)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers_line WHERE product_id=?", Long.class, productId)).isEqualTo(5);

        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/branches", enable(branch, "1")), 409, "PRODUCT_INACTIVE");
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/deactivate", Map.of("active", true)), 400, "VALIDATION_FAILED");
        admin.csrf();
        assertProblem(admin.delete("/api/v1/products/" + product), 405, "METHOD_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_product WHERE id=?", Long.class, productId)).isEqualTo(1);
    }

    @Test
    void productWithoutHistoryIsStillOnlyDeactivatedAndDatabaseRoleCannotDeleteIt() throws Exception {
        createAccount("product-history-admin", "ADMIN", null);
        Browser admin = authenticated("product-history-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-NO-HISTORY", "Sin historia", null, "unidad", false, List.of())).body()).path("externalId").asText());
        admin.csrf();
        assertThat(admin.post("/api/v1/products/" + product + "/deactivate", Map.of()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT active FROM catalog_product WHERE external_id=?", Boolean.class, product)).isFalse();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM catalog_product WHERE external_id=?", product))
            .rootCause().hasMessageContaining("permission denied");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_product WHERE external_id=?", Long.class, product)).isEqualTo(1);
    }

    @Test
    void reactivationRestoresCatalogAvailabilityWithoutRestoringLocalEnablementOrMinimums() throws Exception {
        createAccount("product-reactivate-admin", "ADMIN", null);
        UUID branch = createBranch("PR-BRANCH", true);
        Browser admin = authenticated("product-reactivate-admin");
        admin.csrf();
        UUID product = UUID.fromString(json.readTree(admin.post("/api/v1/products",
            product("SKU-REACTIVATE", "Reactivable", null, "unidad", false, List.of(enable(branch, "7")))).body())
            .path("externalId").asText());
        admin.csrf();
        assertThat(admin.post("/api/v1/products/" + product + "/deactivate", Map.of()).statusCode()).isEqualTo(200);
        admin.csrf();
        assertThat(admin.post("/api/v1/products/" + product + "/update", update("SKU-REACTIVATE", "Renombrado", null, "unidad", false))
            .statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT active FROM catalog_product WHERE external_id=?", Boolean.class, product)).isFalse();

        admin.csrf();
        HttpResponse<String> reactivated = admin.post("/api/v1/products/" + product + "/reactivate", Map.of());
        assertThat(reactivated.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(reactivated.body());
        assertThat(body.path("active").asBoolean()).isTrue();
        assertThat(body.path("externalId").asText()).isEqualTo(product.toString());
        assertThat(body.path("branches").get(0).path("enabled").asBoolean()).isFalse();
        assertThat(body.path("branches").get(0).path("minimumQuantity").isNull()).isTrue();

        admin.csrf();
        HttpResponse<String> enabled = admin.post("/api/v1/products/" + product + "/branches", enable(branch, "2"));
        assertThat(enabled.statusCode()).isEqualTo(200);
        assertThat(json.readTree(enabled.body()).path("branches").get(0).path("minimumQuantity").asText()).isEqualTo("2.000");
    }

    private void assertBlocked(Browser admin, UUID product) throws Exception {
        String before = catalogDigest();
        admin.csrf();
        assertProblem(admin.post("/api/v1/products/" + product + "/deactivate", Map.of()), 409, "PRODUCT_DEACTIVATION_BLOCKED");
        assertThat(catalogDigest()).isEqualTo(before);
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
