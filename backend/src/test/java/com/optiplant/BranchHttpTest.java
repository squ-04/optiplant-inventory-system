package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    properties = {"app.admin-code=branch-bootstrap-admin", "app.admin-password=temporary branch bootstrap"})
class BranchHttpTest {
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
    void rejectsIdentityPayloadsOnDeactivationWithoutChangingAccountsOrBranches() throws Exception {
        createAccount("strict-body-admin", "ADMIN", null);
        UUID branch = createBranch("STRICT-BODY", "Original", true);
        long userId = createAccount("strict-body-user", "OPERATOR", branch);
        UUID user = jdbc.queryForObject("SELECT external_id FROM identity_user_account WHERE id=?", UUID.class, userId);
        Browser admin = authenticated("strict-body-admin");
        String before = identityDigest();
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate",
            Map.of("code", "replacement-code", "active", "true")), 400, "VALIDATION_FAILED");
        assertThat(identityDigest()).isEqualTo(before);
        admin.csrf();
        assertProblem(admin.post("/api/v1/users/" + user + "/deactivate",
            Map.of("externalId", UUID.randomUUID().toString(), "active", "true")), 400, "VALIDATION_FAILED");
        assertThat(identityDigest()).isEqualTo(before);
    }

    private String identityDigest() {
        StringBuilder digest = new StringBuilder();
        for (String table : new String[]{"identity_branch", "identity_user_account"}) {
            digest.append(jdbc.queryForObject("SELECT md5(coalesce(string_agg(row_to_json(t)::text, '' ORDER BY id),'')) FROM "
                + table + " t", String.class));
        }
        return digest.toString();
    }

    @Test
    void administratorManagesBranchesWithoutExposingInternalIdentifiers() throws Exception {
        createAccount("branch-admin", "ADMIN", null);
        Browser admin = authenticated("branch-admin");
        admin.csrf();
        HttpResponse<String> created = admin.post("/api/v1/branches", Map.of("code", "BRANCH-LIFE", "name", "Casa matriz"));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode branch = json.readTree(created.body());
        UUID externalId = UUID.fromString(branch.path("externalId").asText());
        assertThat(externalId.version()).isEqualTo(4);
        assertThat(branch.path("code").asText()).isEqualTo("BRANCH-LIFE");
        assertThat(branch.path("name").asText()).isEqualTo("Casa matriz");
        assertThat(branch.path("active").asBoolean()).isTrue();
        assertThat(branch.has("id")).isFalse();

        assertThat(admin.get("/api/v1/branches").body()).contains("BRANCH-LIFE");
        assertThat(admin.get("/api/v1/branches/" + externalId).statusCode()).isEqualTo(200);
        admin.csrf();
        assertThat(admin.post("/api/v1/branches/" + externalId + "/update", Map.of("name", "Casa central"))
            .statusCode()).isEqualTo(200);
        admin.csrf();
        assertThat(admin.post("/api/v1/branches/" + externalId + "/deactivate", Map.of()).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/v1/branches/" + externalId).body()).path("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT active FROM identity_branch WHERE external_id=?", Boolean.class, externalId)).isFalse();
    }

    @Test
    void rejectsDuplicatesInvalidAndImmutableBranchFieldsWithoutChangingData() throws Exception {
        createAccount("branch-validation-admin", "ADMIN", null);
        Browser admin = authenticated("branch-validation-admin");
        UUID existing = createBranch("BRANCH-UNIQUE", "Original", false);
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches", Map.of("code", "BRANCH-UNIQUE", "name", "Duplicada")), 409,
            "BRANCH_CODE_EXISTS");
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches", Map.of("code", " ", "name", "Válida")), 400, "VALIDATION_FAILED");
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches", Map.of("code", "NEW", "name", "Válida", "active", "false")), 400,
            "VALIDATION_FAILED");
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches/" + existing + "/update", Map.of("name", "Cambio", "code", "MUTABLE")),
            400, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT name FROM identity_branch WHERE external_id=?", String.class, existing))
            .isEqualTo("Original");
    }

    @Test
    void nonAdministratorsCanReadOnlyTheirOwnBranchAndCannotProbeOthers() throws Exception {
        UUID own = createBranch("BRANCH-OWN", "Propia", true);
        UUID other = createBranch("BRANCH-OTHER", "Ajena", true);
        createAccount("branch-manager", "MANAGER", own);
        Browser manager = authenticated("branch-manager");
        assertThat(manager.get("/api/v1/branches").body()).contains(own.toString()).doesNotContain(other.toString());
        assertThat(manager.get("/api/v1/branches/" + own).statusCode()).isEqualTo(200);
        assertProblem(manager.get("/api/v1/branches/" + other), 403, "BRANCH_FORBIDDEN");
        assertProblem(manager.get("/api/v1/branches/" + UUID.randomUUID()), 403, "BRANCH_FORBIDDEN");
        manager.csrf();
        assertProblem(manager.post("/api/v1/branches", Map.of("code", "FORBIDDEN", "name", "No")), 403, "ROLE_FORBIDDEN");
    }

    @Test
    void deactivationRejectsStockPurchasesAndOpenTransferStatesWithoutMutatingTheBranch() throws Exception {
        createAccount("branch-impediment-admin", "ADMIN", null);
        Browser admin = authenticated("branch-impediment-admin");
        UUID branch = createBranch("BRANCH-IMPEDIMENT", "Impedimentos", true);
        UUID other = createBranch("BRANCH-IMPEDIMENT-OTHER", "Otra", true);
        long accountId = createAccount("branch-impediment-operator", "OPERATOR", branch);

        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), "STOCK-BLOCKER", "Producto", "unidad", false);
        jdbc.update("INSERT INTO inventory_balance(branch_id,product_id,physical_qty,inventory_value,average_base_cost,history_started_at) "
                + "VALUES ((SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM catalog_product WHERE sku=?),1,1,1,CURRENT_TIMESTAMP)",
            branch, "STOCK-BLOCKER");
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()), 409, "BRANCH_DEACTIVATION_BLOCKED");
        jdbc.update("UPDATE inventory_balance SET physical_qty=0,inventory_value=0 WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?)", branch);

        jdbc.update("INSERT INTO purchases_supplier(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), "SUP-BLOCKER", "Proveedor");
        for (String status : new String[] {"DRAFT", "PENDING"}) {
            jdbc.update("INSERT INTO purchases_order(external_id,code,branch_id,supplier_id,supplier_name_snapshot,status,created_by,payment_term_days) "
                    + "VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM purchases_supplier WHERE code=?),?,?,?,0)",
                UUID.randomUUID(), "ORDER-" + status, branch, "SUP-BLOCKER", "Proveedor", status, accountId);
            admin.csrf();
            assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()), 409, "BRANCH_DEACTIVATION_BLOCKED");
            jdbc.update("UPDATE purchases_order SET status='CANCELLED' WHERE code=?", "ORDER-" + status);
        }

        for (String status : new String[] {"REQUESTED", "PREPARING", "APPROVED", "IN_TRANSIT"}) {
            jdbc.update("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,requested_by,requested_at,status) "
                    + "VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,?)",
                UUID.randomUUID(), "TRANSFER-" + status, branch, other, accountId, status);
            admin.csrf();
            assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()), 409, "BRANCH_DEACTIVATION_BLOCKED");
            jdbc.update("UPDATE transfers_transfer SET status='CANCELLED' WHERE code=?", "TRANSFER-" + status);
        }

        long productId = jdbc.queryForObject("SELECT id FROM catalog_product WHERE sku=?", Long.class, "STOCK-BLOCKER");
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) VALUES (?,?,1,false,true)",
            productId, "unidad");
        long transferId = jdbc.queryForObject("INSERT INTO transfers_transfer(external_id,code,origin_branch_id,destination_branch_id,requested_by,requested_at,status) "
                + "VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),?,CURRENT_TIMESTAMP,'RECEIVED_DISCREPANCY') RETURNING id",
            Long.class, UUID.randomUUID(), "TRANSFER-DISCREPANCY", branch, other, accountId);
        long lineId = jdbc.queryForObject("INSERT INTO transfers_line(transfer_id,line_no,product_id,product_unit_id,sku_snapshot,product_name_snapshot,unit_name_snapshot,factor_snapshot,requested_commercial_qty,requested_base_qty) "
                + "VALUES (?,?,?,(SELECT id FROM catalog_product_unit WHERE product_id=? AND is_base),?,?,?,1,1,1) RETURNING id",
            Long.class, transferId, 1, productId, productId, "STOCK-BLOCKER", "Producto", "unidad");
        long routeId = jdbc.queryForObject("INSERT INTO logistics_route(external_id,code,origin_branch_id,destination_branch_id,priority) "
                + "VALUES (?,?,(SELECT id FROM identity_branch WHERE external_id=?),(SELECT id FROM identity_branch WHERE external_id=?),'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), "ROUTE-BLOCKER", branch, other);
        long dispatchId = jdbc.queryForObject("INSERT INTO logistics_dispatch(external_id,transfer_id,route_id,carrier_text,dispatched_at,estimated_arrival_at,dispatched_by,route_priority_snapshot) "
                + "VALUES (?,?,?,'Transportista',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'LOW') RETURNING id",
            Long.class, UUID.randomUUID(), transferId, routeId, accountId);
        long dispatchLineId = jdbc.queryForObject("INSERT INTO logistics_dispatch_line(dispatch_id,transfer_line_id,dispatched_commercial_qty,dispatched_base_qty,frozen_base_cost,base_value) "
                + "VALUES (?,?,1,1,1,1) RETURNING id", Long.class, dispatchId, lineId);
        jdbc.update("INSERT INTO inventory_transit(dispatch_line_id,pending_base_qty,pending_base_value) VALUES (?,1,1)", dispatchLineId);
        long arrivalId = jdbc.queryForObject("INSERT INTO logistics_arrival(external_id,dispatch_id,arrived_at,confirmed_by) "
                + "VALUES (?,?,CURRENT_TIMESTAMP,?) RETURNING id", Long.class, UUID.randomUUID(), dispatchId, accountId);
        long arrivalLineId = jdbc.queryForObject("INSERT INTO logistics_arrival_line(arrival_id,dispatch_line_id,received_commercial_qty,received_base_qty) "
                + "VALUES (?,?,0,0) RETURNING id", Long.class, arrivalId, dispatchLineId);
        jdbc.update("INSERT INTO logistics_discrepancy(external_id,arrival_id,opened_at,status) VALUES (?,?,CURRENT_TIMESTAMP,'OPEN')",
            UUID.randomUUID(), arrivalId);
        jdbc.update("INSERT INTO logistics_discrepancy_line(discrepancy_id,arrival_line_id,missing_base_qty) "
                + "VALUES ((SELECT id FROM logistics_discrepancy WHERE arrival_id=?),?,1)", arrivalId, arrivalLineId);
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()), 409, "BRANCH_DEACTIVATION_BLOCKED");
        jdbc.update("UPDATE inventory_transit SET pending_base_qty=0,pending_base_value=0 WHERE dispatch_line_id=?", dispatchLineId);
        admin.csrf();
        assertProblem(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()), 409, "BRANCH_DEACTIVATION_BLOCKED");
        jdbc.update("UPDATE logistics_discrepancy SET status='RESOLVED',treatment='CLAIM',resolved_by=?,resolved_at=CURRENT_TIMESTAMP WHERE arrival_id=?",
            accountId, arrivalId);

        admin.csrf();
        assertThat(admin.post("/api/v1/branches/" + branch + "/deactivate", Map.of()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT active FROM identity_user_account WHERE id=?", Boolean.class, accountId)).isTrue();
    }

    private UUID createBranch(String code, String name, boolean active) {
        UUID externalId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name,active) VALUES (?,?,?,?)", externalId, code, name, active);
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

        HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header(header, token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }

        URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    }
}
