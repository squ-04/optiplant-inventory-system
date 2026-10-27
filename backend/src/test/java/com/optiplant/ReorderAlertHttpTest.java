package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
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

/** RF-0019 minimum stock control and RF-0020 restock alerts, through the public HTTP API. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=alerts-initial", "app.admin-password=private initial password"})
class ReorderAlertHttpTest {
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
    UUID a, b, product;
    String suffix;
    Browser admin, operator;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString();
        a = branch("a");
        b = branch("b");
        product = product();
        admin = account("ADMIN", null);
        operator = account("OPERATOR", a);
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    /** An indivisible product with base unit "unidad", not enabled anywhere yet. */
    UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,'Maceta','unidad',false)",
            id, "SKU-" + UUID.randomUUID());
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        return id;
    }

    Browser account(String role, UUID branch) throws Exception {
        String code = role + UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
                + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)",
            UUID.randomUUID(), code, "Persona " + role, passwords.encode("private individual password"), role, branch);
        Browser browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password")).statusCode())
            .isEqualTo(200);
        return browser;
    }

    void enable(UUID branch, String minimum) throws Exception {
        assertThat(admin.post("/products/" + product + "/branches",
            Map.of("branchExternalId", branch.toString(), "minimumQuantity", minimum)).statusCode()).isEqualTo(200);
    }

    void minimum(UUID branch, String minimum) throws Exception {
        assertThat(admin.post("/products/" + product + "/branches/" + branch + "/minimum", Map.of("minimumQuantity", minimum))
            .statusCode()).isEqualTo(200);
    }

    void move(String kind, String quantity) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("kind", kind, "product", product.toString(), "unit", "unidad",
            "quantity", quantity, "reason", "Movimiento de prueba"));
        if (kind.equals("MANUAL_ENTRY") && stock(a).equals("0")) body.put("unitCost", "10");
        HttpResponse<String> response = operator.post("/inventory/movements", body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    String stock(UUID branch) {
        return jdbc.query("SELECT trim_scale(ib.physical_qty) FROM inventory_balance ib JOIN identity_branch b ON b.id=ib.branch_id "
            + "JOIN catalog_product p ON p.id=ib.product_id WHERE b.external_id=? AND p.external_id=?",
            (r, i) -> r.getString(1), branch, product).stream().findFirst().orElse("0");
    }

    /** Stored episodes of the product in a branch, oldest first: "status minimumSnapshot quantitySnapshot". */
    List<String> episodes(UUID branch) {
        return jdbc.queryForList("SELECT CASE WHEN a.resolved_at IS NULL THEN 'ACTIVE' ELSE 'RESOLVED' END||' '||"
            + "trim_scale(a.minimum_snapshot)||' '||trim_scale(a.quantity_snapshot) FROM inventory_reorder_alert a "
            + "JOIN catalog_branch_product bp ON bp.id=a.branch_product_id JOIN identity_branch b ON b.id=bp.branch_id "
            + "JOIN catalog_product p ON p.id=bp.product_id WHERE b.external_id=? AND p.external_id=? ORDER BY a.id",
            String.class, branch, product);
    }

    JsonNode read(Browser browser, String query) throws Exception {
        HttpResponse<String> response = browser.get("/inventory/reorder-alerts" + query);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    boolean localIndicator() throws Exception {
        for (JsonNode item : json.readTree(operator.get("/products/local").body()).get("items")) {
            if (item.get("productExternalId").asText().equals(product.toString())) return item.get("restockAlert").asBoolean();
        }
        throw new AssertionError("product not in local catalog");
    }

    @Test void availableAtOrBelowTheMinimumKeepsOneActiveAlertThatResolvesAboveIt() throws Exception {
        int purchases = jdbc.queryForObject("SELECT count(*) FROM purchases_order", Integer.class);
        // RF-0019-CA-03: minimum 5, stock 0 → the condition holds as soon as the combination is enabled.
        enable(a, "5");
        assertThat(episodes(a)).containsExactly("ACTIVE 5 0");
        assertThat(localIndicator()).isTrue();
        move("MANUAL_ENTRY", "4");
        // RF-0020-CA-02: repeated evaluations at 4 and at 5 (equality) keep a single active episode.
        move("MANUAL_ENTRY", "1");
        assertThat(stock(a)).isEqualTo("5");
        assertThat(episodes(a)).containsExactly("ACTIVE 5 0");
        move("MANUAL_ENTRY", "1");
        assertThat(stock(a)).isEqualTo("6");
        assertThat(episodes(a)).containsExactly("RESOLVED 5 0");
        assertThat(localIndicator()).isFalse();
        // Going back to the minimum opens a new episode; the resolved one stays as history.
        move("MANUAL_EXIT", "1");
        assertThat(episodes(a)).containsExactly("RESOLVED 5 0", "ACTIVE 5 5");

        JsonNode history = read(admin, "?branch=" + a + "&status=ALL");
        assertThat(history.get("activeCount").asInt()).isEqualTo(1);
        assertThat(history.get("branch").get("externalId").asText()).isEqualTo(a.toString());
        assertThat(history.get("items")).hasSize(2);
        JsonNode active = history.get("items").get(0);
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(active.get("resolvedAt").isNull()).isTrue();
        assertThat(active.get("product").get("externalId").asText()).isEqualTo(product.toString());
        assertThat(active.get("minimumAtOpening").asText()).isEqualTo("5.000");
        assertThat(active.get("quantityAtOpening").asText()).isEqualTo("5.000");
        assertThat(active.get("currentMinimum").asText()).isEqualTo("5.000");
        assertThat(active.get("currentQuantity").asText()).isEqualTo("5.000");
        JsonNode resolved = history.get("items").get(1);
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolvedAt").isNull()).isFalse();
        assertThat(read(admin, "?branch=" + a).get("items")).hasSize(1);
        // No automatic purchase is created (RF-0020-CA-02).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchases_order", Integer.class)).isEqualTo(purchases);
    }

    @Test void aChangedMinimumIsUsedByTheNextEvaluationAndZeroAlertsOnlyAtZeroStock() throws Exception {
        enable(a, "0");
        // Minimum zero alerts at zero stock.
        assertThat(episodes(a)).containsExactly("ACTIVE 0 0");
        move("MANUAL_ENTRY", "6");
        assertThat(episodes(a)).containsExactly("RESOLVED 0 0");
        // RF-0019-CA-02: raising the minimum to the current stock (equality) opens an episode with the new value.
        minimum(a, "6");
        assertThat(episodes(a)).containsExactly("RESOLVED 0 0", "ACTIVE 6 6");
        minimum(a, "5");
        assertThat(episodes(a)).containsExactly("RESOLVED 0 0", "RESOLVED 6 6");
        // Changing the minimum does not move stock.
        assertThat(stock(a)).isEqualTo("6");
    }

    @Test void deactivatingTheProductOrTheBranchClosesEpisodesWithoutReplenishingStock() throws Exception {
        enable(a, "2");
        enable(b, "1");
        assertThat(episodes(a)).containsExactly("ACTIVE 2 0");
        assertThat(admin.post("/products/" + product + "/deactivate", null).statusCode()).isEqualTo(200);
        assertThat(episodes(a)).containsExactly("RESOLVED 2 0");
        assertThat(episodes(b)).containsExactly("RESOLVED 1 0");
        assertThat(read(admin, "?branch=" + a).get("activeCount").asInt()).isZero();
        // Reactivating and enabling again evaluates anew.
        assertThat(admin.post("/products/" + product + "/reactivate", null).statusCode()).isEqualTo(200);
        enable(b, "1");
        assertThat(episodes(b)).containsExactly("RESOLVED 1 0", "ACTIVE 1 0");
        assertThat(admin.post("/branches/" + b + "/deactivate", null).statusCode()).isEqualTo(200);
        assertThat(episodes(b)).containsExactly("RESOLVED 1 0", "RESOLVED 1 0");
    }

    @Test void managersReadTheirOwnBranchAdministratorsTheNetworkAndOperatorsOnlyTheirIndicator() throws Exception {
        enable(a, "1");
        enable(b, "1");
        Browser manager = account("MANAGER", a);
        JsonNode own = read(manager, "");
        assertThat(own.get("branch").get("externalId").asText()).isEqualTo(a.toString());
        assertThat(own.get("items")).allSatisfy(item -> assertThat(item.get("branch").get("externalId").asText()).isEqualTo(a.toString()));
        assertThat(own.get("items").findValuesAsString("externalId")).isNotEmpty();
        assertThat(error(manager.get("/inventory/reorder-alerts?branch=" + b), 403)).isEqualTo("BRANCH_FORBIDDEN");
        assertThat(error(operator.get("/inventory/reorder-alerts"), 403)).isEqualTo("ROLE_FORBIDDEN");
        assertThat(error(operator.get("/inventory/reorder-alerts?branch=" + a), 403)).isEqualTo("ROLE_FORBIDDEN");

        JsonNode network = read(admin, "");
        assertThat(network.get("branch").isNull()).isTrue();
        List<String> branches = network.get("items").findValues("branch").stream().map(node -> node.get("externalId").asText()).toList();
        assertThat(branches).contains(a.toString(), b.toString());
        assertThat(network.get("activeCount").asInt()).isGreaterThanOrEqualTo(2);

        assertThat(error(admin.get("/inventory/reorder-alerts?status=RESOLVED"), 400)).isEqualTo("VALIDATION_FAILED");
        assertThat(error(admin.get("/inventory/reorder-alerts?limit=0"), 400)).isEqualTo("VALIDATION_FAILED");
        assertThat(error(admin.get("/inventory/reorder-alerts?branch=" + UUID.randomUUID()), 404)).isEqualTo("BRANCH_NOT_FOUND");
        assertThat(new Browser().get("/inventory/reorder-alerts").statusCode()).isEqualTo(401);
    }

    String error(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).get("code").asText();
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, Object body) throws Exception {
            JsonNode csrf = json.readTree(get("/auth/csrf").body());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf.has("headerName")) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
