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
