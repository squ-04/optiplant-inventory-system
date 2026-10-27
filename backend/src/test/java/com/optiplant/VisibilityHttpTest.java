package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=visibility-initial", "app.admin-password=private initial password"})
class VisibilityHttpTest {
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
    UUID a, b;
    String suffix;

    @BeforeEach void data() {
        suffix = UUID.randomUUID().toString(); a = branch("a"); b = branch("b");
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,'unidad',false)",
            UUID.randomUUID(), suffix, "Producto de prueba");
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "SELECT b.id,p.id,7,21,3,CURRENT_TIMESTAMP FROM identity_branch b CROSS JOIN catalog_product p WHERE b.external_id=? AND p.sku=?", a, suffix);
    }
    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, code);
        return id;
    }
    Browser account(String role, UUID branch) throws Exception {
        String code = role + UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
            + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)",
            UUID.randomUUID(), code, "Persona de prueba", passwords.encode("private individual password"), role, branch);
        Browser browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password")).statusCode()).isEqualTo(200);
        return browser;
    }
    @Test void adminSeesActualInventoryAcrossBranchesWithoutSecretsOrInternalKeys() throws Exception {
        Browser admin = account("ADMIN", null);
        var first = admin.get("/visibility/branches/" + a);
        assertThat(first.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(first.body());
        assertThat(body.get("inventory").get(0).get("physicalQuantity").asText()).isEqualTo("7.000");
        assertThat(first.body()).doesNotContain("password", "credentialsVersion", "branch_id", "product_id");
        assertThat(admin.get("/visibility/branches/" + b).statusCode()).isEqualTo(200);
        assertThat(admin.get("/visibility/branches/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(admin.get("/visibility/branches/not-a-uuid").statusCode()).isEqualTo(400);
    }
    @Test void localProfilesReadOwnHistoryButCannotSubstituteAnotherBranch() throws Exception {
        for (String role : new String[]{"MANAGER", "OPERATOR"}) {
            Browser local = account(role, a);
            assertThat(local.get("/visibility/branches/" + a).statusCode()).isEqualTo(200);
            assertThat(local.get("/visibility/branches/" + b).statusCode()).isEqualTo(403);
            assertThat(local.get("/visibility/branches/" + UUID.randomUUID()).statusCode()).isEqualTo(403);
            var inventory = local.get("/visibility/inventory/" + b);
            assertThat(inventory.statusCode()).isEqualTo(200);
            assertThat(inventory.body()).doesNotContain("cost", "value", "password");
        }
        assertThat(jdbc.queryForObject("SELECT physical_qty FROM inventory_balance ib JOIN identity_branch b ON b.id=ib.branch_id WHERE b.external_id=?", String.class, a)).isEqualTo("7.000");
    }
    // RF-0014-CA-01: the consulted branch's stock, identified, with physical, available and transit apart; no valuation.
    @Test void everyProfileConsultsAnotherBranchStockWithTransitApartAndWithoutValuation() throws Exception {
        long product = jdbc.queryForObject("SELECT id FROM catalog_product WHERE sku=?", Long.class, suffix);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) VALUES (?,'unidad',1,false,true)", product);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
            + "SELECT b.id,?,2,6,3,CURRENT_TIMESTAMP FROM identity_branch b WHERE b.external_id=?", product, b);
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,'Habilitado sin stock','kg',true)",
            UUID.randomUUID(), suffix + "-empty");
        jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty) SELECT b.id,p.id,true,0 "
            + "FROM identity_branch b CROSS JOIN catalog_product p WHERE b.external_id=? AND p.sku=?", a, suffix + "-empty");
        Browser sender = account("OPERATOR", b);
        transit(b, a, product, jdbc.queryForObject("SELECT u.id FROM identity_user_account u JOIN identity_branch b ON b.id=u.branch_id "
            + "WHERE b.external_id=? AND u.role='OPERATOR'", Long.class, b), "1.5");

        for (Browser reader : new Browser[]{sender, account("MANAGER", b), account("ADMIN", null)}) {
            var response = reader.get("/visibility/branches/" + a + "/inventory");
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json.readTree(response.body());
            assertThat(body.get("branch").get("externalId").asText()).isEqualTo(a.toString());
            assertThat(body.get("branch").get("code").asText()).isEqualTo("a" + suffix);
            assertThat(body.get("readAt").asText()).isNotBlank();
            JsonNode items = body.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("sku").asText()).isEqualTo(suffix);
            assertThat(items.get(0).get("baseUnitName").asText()).isEqualTo("unidad");
            assertThat(items.get(0).get("physicalQuantity").asText()).isEqualTo("7.000");
            assertThat(items.get(0).get("availableQuantity").asText()).isEqualTo("7.000");
            assertThat(items.get(0).get("inTransitQuantity").asText()).isEqualTo("1.500");
            assertThat(items.get(1).get("sku").asText()).isEqualTo(suffix + "-empty");
            assertThat(items.get(1).get("physicalQuantity").asText()).isEqualTo("0.000");
            assertThat(items.get(1).get("inTransitQuantity").asText()).isEqualTo("0.000");
            assertThat(response.body()).doesNotContain("Cost", "Value", "value\"", "password", "branch_id", "product_id", "\"id\"");
        }
        // The own branch shows its own figures, not the consulted ones; transit towards A is not stock of B.
        JsonNode own = json.readTree(sender.get("/visibility/branches/" + b + "/inventory").body());
        assertThat(own.get("branch").get("externalId").asText()).isEqualTo(b.toString());
        assertThat(own.get("items")).hasSize(1);
        assertThat(own.get("items").get(0).get("physicalQuantity").asText()).isEqualTo("2.000");
        assertThat(own.get("items").get(0).get("inTransitQuantity").asText()).isEqualTo("0.000");
        // Reading never writes.
        assertThat(jdbc.queryForObject("SELECT physical_qty FROM inventory_balance ib JOIN identity_branch b ON b.id=ib.branch_id "
            + "WHERE b.external_id=? AND ib.product_id=?", String.class, a, product)).isEqualTo("7.000");
    }
    // RF-0015-CA-01: a confirmed change at the origin is observed by the next read from another branch.
    @Test void confirmedChangeIsVisibleOnTheNextReadFromAnotherBranch() throws Exception {
        Browser reader = account("OPERATOR", b);
        JsonNode before = json.readTree(reader.get("/visibility/branches/" + a + "/inventory").body());
        assertThat(before.get("items").get(0).get("physicalQuantity").asText()).isEqualTo("7.000");
        jdbc.update("UPDATE inventory_balance SET physical_qty=4,inventory_value=12,updated_at=CURRENT_TIMESTAMP "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?)", a);
        JsonNode after = json.readTree(reader.get("/visibility/branches/" + a + "/inventory").body());
        assertThat(after.get("items").get(0).get("physicalQuantity").asText()).isEqualTo("4.000");
        assertThat(after.get("items").get(0).get("availableQuantity").asText()).isEqualTo("4.000");
        assertThat(java.time.Instant.parse(after.get("readAt").asText())).isAfterOrEqualTo(java.time.Instant.parse(before.get("readAt").asText()));
    }
    @Test void branchInventoryRejectsUnknownMalformedAndAnonymousReadsAndKeepsInactiveBranchesReadable() throws Exception {
        Browser manager = account("MANAGER", b);
        assertThat(manager.get("/visibility/branches/" + UUID.randomUUID() + "/inventory").statusCode()).isEqualTo(404);
        assertThat(manager.get("/visibility/branches/not-a-uuid/inventory").statusCode()).isEqualTo(400);
        assertThat(manager.get("/visibility/branches/00000000-0000-1000-8000-000000000000/inventory").statusCode()).isEqualTo(400);
        assertThat(new Browser().get("/visibility/branches/" + a + "/inventory").statusCode()).isEqualTo(401);
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        var inactive = manager.get("/visibility/branches/" + a + "/inventory");
        assertThat(inactive.statusCode()).isEqualTo(200);
        assertThat(json.readTree(inactive.body()).get("branch").get("active").asBoolean()).isFalse();
    }
    void transit(UUID origin, UUID destination, long productId, long actor, String quantity) {
        long unitId = jdbc.queryForObject("SELECT id FROM catalog_product_unit WHERE product_id=? AND is_base", Long.class, productId);
        long transferId = jdbc.queryForObject("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,"
                + "requested_by,requested_at,status) VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),"
                + "(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,'IN_TRANSIT') RETURNING id",
            Long.class, UUID.randomUUID(), "TR-" + suffix, origin, destination, actor);
        long lineId = jdbc.queryForObject("INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,"
                + "product_name_snapshot,unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty) "
                + "VALUES (?,1,?,?,'SKU','Producto','unidad',1,?::numeric,?::numeric) RETURNING id", Long.class, transferId, productId, unitId,
            quantity, quantity);
        long routeId = jdbc.queryForObject("INSERT INTO logistics_route(external_id,code,origin_branch_id,destination_branch_id,priority) "
                + "VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), "RT-" + suffix, origin, destination);
        long dispatchId = jdbc.queryForObject("INSERT INTO logistics_dispatch(external_id,transfer_id,route_id,carrier_text,dispatched_at,"
                + "estimated_arrival_at,dispatched_by,route_priority_snapshot) VALUES (?,?,?,'Transportista',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), transferId, routeId, actor);
        long dispatchLineId = jdbc.queryForObject("INSERT INTO logistics_dispatch_line(dispatch_id,transfer_line_id,dispatched_commercial_qty,"
            + "dispatched_base_qty,frozen_base_cost,base_value) VALUES (?,?,?::numeric,?::numeric,1,?::numeric) RETURNING id", Long.class,
            dispatchId, lineId, quantity, quantity, quantity);
        jdbc.update("INSERT INTO inventory_transit(dispatch_line_id,pending_base_qty,pending_base_value) VALUES (?,?::numeric,1)",
            dispatchLineId, quantity);
    }
    @Test void inactiveBranchRetainsHistoricalAccessAndDoesNotDisableItsUser() throws Exception {
        Browser manager = account("MANAGER", a);
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", a);
        assertThat(manager.get("/auth/me").statusCode()).isEqualTo(200);
        assertThat(manager.get("/visibility/branches/" + a).statusCode()).isEqualTo(200);
        assertThat(account("OPERATOR", a).get("/visibility/branches/" + a).statusCode()).isEqualTo(200);
    }
    @Test void unauthenticatedCannotReadDirectoryOrHistory() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.get("/visibility/branches").statusCode()).isEqualTo(401);
        assertThat(browser.get("/visibility/branches/" + a).statusCode()).isEqualTo(401);
    }
    class Browser {
        HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, Object body) throws Exception {
            JsonNode csrf = json.readTree(get("/auth/csrf").body());
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json").header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
