package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.catalog.service.PriceListService;
import com.optiplant.common.exception.ApiException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/** RF-0030: global price lists maintained by the administrator, consulted and used by local profiles. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=prices-initial", "app.admin-password=private initial password"})
class PriceListHttpTest {
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
    @Autowired PriceListService priceLists;
    UUID branch, product;
    Browser admin, manager, operator;

    @BeforeEach void data() throws Exception {
        branch = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", branch, "S-" + branch, "Sucursal");
        product = product();
        admin = account("ADMIN", null);
        manager = account("MANAGER", branch);
        operator = account("OPERATOR", branch);
    }

    /** An indivisible product with base unit "unidad", commercial unit "caja" of 12 and an inactive unit "bulto". */
    UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,'unidad',false)",
            id, "SKU-" + id, "Maceta");
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'caja',12,false,false FROM catalog_product WHERE external_id=?", id);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base,active) "
            + "SELECT id,'bulto',24,false,false,false FROM catalog_product WHERE external_id=?", id);
        return id;
    }

    Browser account(String role, UUID at) throws Exception {
        String code = role + UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
                + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)",
            UUID.randomUUID(), code, "Persona " + role, passwords.encode("private individual password"), role, at);
        Browser browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password")).statusCode())
            .isEqualTo(200);
        return browser;
    }

    String list(String name) throws Exception {
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", "LP-" + UUID.randomUUID(), "name", name));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    HttpResponse<String> price(Browser who, String list, String unit, String price) throws Exception {
        return who.post("/catalog/price-lists/" + list + "/prices", Map.of("product", product.toString(), "unit", unit, "price", price));
    }

    /** "unit=price" pairs of a list as seen by the given profile. */
    List<String> prices(Browser who, String list) throws Exception {
        HttpResponse<String> detail = who.get("/catalog/price-lists/" + list);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        return read(detail).get("prices").valueStream().map(p -> p.get("unitName").asText() + "=" + p.get("price").asText()).toList();
    }

    long unitId(String name) {
        return jdbc.queryForObject("SELECT u.id FROM catalog_product_unit u JOIN catalog_product p ON p.id=u.product_id "
            + "WHERE p.external_id=? AND u.name=?", Long.class, product, name);
    }

    long entries() {
        return jdbc.queryForObject("SELECT count(*) FROM catalog_price_entry", Long.class);
    }

    JsonNode read(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    @Test void twoListsKeepTheirOwnPriceAndTheSaleTakesTheChosenOne() throws Exception {
        String retail = list("Detal");
        String wholesale = list("Mayorista");
        assertThat(price(admin, retail, "caja", "120000").statusCode()).isEqualTo(200);
        assertThat(price(admin, retail, "unidad", "11000.5").statusCode()).isEqualTo(200);
        assertThat(price(admin, wholesale, "caja", "99999.123456").statusCode()).isEqualTo(200);

        // RF-0030-CA-01: each list keeps its own price for the same product and unit, for every profile.
        for (Browser who : List.of(admin, manager, operator)) {
            assertThat(prices(who, retail)).containsExactly("unidad=11000.5", "caja=120000");
            assertThat(prices(who, wholesale)).containsExactly("caja=99999.123456");
        }
        // The price used when confirming a sale is the one of the chosen list; another unit is not sold with it.
        assertThat(priceLists.price(UUID.fromString(retail), unitId("caja")).grossPrice()).isEqualByComparingTo("120000");
        PriceListService.ListPrice chosen = priceLists.price(UUID.fromString(wholesale), unitId("caja"));
        assertThat(chosen.grossPrice()).isEqualByComparingTo("99999.123456");
        assertThat(chosen.listName()).isEqualTo("Mayorista");
        assertThatThrownBy(() -> priceLists.price(UUID.fromString(wholesale), unitId("unidad")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRICE_NOT_DEFINED"));

        // Replacing a price changes only that entry; zero is a valid price.
        assertThat(price(admin, retail, "caja", "0").statusCode()).isEqualTo(200);
        assertThat(prices(admin, retail)).containsExactly("unidad=11000.5", "caja=0");
        assertThat(prices(admin, wholesale)).containsExactly("caja=99999.123456");
    }

    @Test void administratorMaintainsListsAndRejectsDuplicatedCodes() throws Exception {
        String code = "LP-" + UUID.randomUUID();
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", code, "name", "  Detal  "));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = read(created).get("externalId").asText();
        assertThat(read(created).get("name").asText()).isEqualTo("Detal");
        assertThat(read(created).get("active").asBoolean()).isTrue();

        HttpResponse<String> duplicated = admin.post("/catalog/price-lists", Map.of("code", code, "name", "Otra"));
        assertThat(duplicated.statusCode()).isEqualTo(409);
        assertThat(read(duplicated).get("code").asText()).isEqualTo("PRICE_LIST_CODE_EXISTS");
        HttpResponse<String> otherCase = admin.post("/catalog/price-lists", Map.of("code", code.toLowerCase(), "name", "Otra"));
        assertThat(otherCase.statusCode()).isEqualTo(409);
        assertThat(read(otherCase).get("code").asText()).isEqualTo("PRICE_LIST_CODE_EXISTS");
        String other = list("Mayorista");
        HttpResponse<String> renamed = admin.post("/catalog/price-lists/" + other + "/update",
            Map.of("code", code, "name", "Mayorista", "active", true));
        assertThat(renamed.statusCode()).isEqualTo(409);

        HttpResponse<String> updated = admin.post("/catalog/price-lists/" + id + "/update",
            Map.of("code", code + "-2", "name", "Detal 2026", "active", true));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(read(admin.get("/catalog/price-lists/" + id)).get("name").asText()).isEqualTo("Detal 2026");
    }

    @Test void inactiveListsAreHiddenFromLocalProfilesAndCannotBeUsed() throws Exception {
        String list = list("Temporada");
        price(admin, list, "caja", "50");
        assertThat(admin.post("/catalog/price-lists/" + list + "/update",
            Map.of("code", "LP-" + UUID.randomUUID(), "name", "Temporada", "active", false)).statusCode()).isEqualTo(200);

        assertThat(read(admin.get("/catalog/price-lists")).valueStream().map(l -> l.get("externalId").asText())).contains(list);
        assertThat(prices(admin, list)).containsExactly("caja=50");
        for (Browser local : List.of(manager, operator)) {
            assertThat(read(local.get("/catalog/price-lists")).valueStream().map(l -> l.get("externalId").asText()))
                .doesNotContain(list);
            assertThat(local.get("/catalog/price-lists/" + list).statusCode()).isEqualTo(404);
        }
        assertThatThrownBy(() -> priceLists.price(UUID.fromString(list), unitId("caja")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("PRICE_LIST_INACTIVE"));
    }

    @Test void onlyTheAdministratorMaintainsListsAndPrices() throws Exception {
        String list = list("Detal");
        long before = entries();
        for (Browser local : List.of(manager, operator)) {
            HttpResponse<String> create = local.post("/catalog/price-lists", Map.of("code", "LP-" + UUID.randomUUID(), "name", "X"));
            assertThat(create.statusCode()).isEqualTo(403);
            assertThat(read(create).get("code").asText()).isEqualTo("ROLE_FORBIDDEN");
            assertThat(local.post("/catalog/price-lists/" + list + "/update",
                Map.of("code", "LP-" + UUID.randomUUID(), "name", "X", "active", false)).statusCode()).isEqualTo(403);
            assertThat(price(local, list, "caja", "1").statusCode()).isEqualTo(403);
        }
        assertThat(entries()).isEqualTo(before);
        assertThat(read(admin.get("/catalog/price-lists/" + list)).get("name").asText()).isEqualTo("Detal");
        assertThat(new Browser().get("/catalog/price-lists").statusCode()).isEqualTo(401);
    }

    @Test void invalidPricesAndUnitsAreRejectedWithoutChangingTheList() throws Exception {
        String list = list("Detal");
        price(admin, list, "caja", "100");
        for (String invalid : List.of("-1", "1.1234567", "1e3", "", "1234567890123456789", "1,5")) {
            HttpResponse<String> rejected = price(admin, list, "caja", invalid);
            assertThat(rejected.statusCode()).as(invalid).isEqualTo(400);
            assertThat(read(rejected).get("code").asText()).isEqualTo("VALIDATION_FAILED");
        }
        assertThat(admin.post("/catalog/price-lists/" + list + "/prices",
            Map.of("product", product.toString(), "unit", "caja", "price", 5)).statusCode()).isEqualTo(400);
        assertThat(admin.post("/catalog/price-lists/" + list + "/prices",
            Map.of("product", product.toString(), "unit", "caja", "price", "5", "discount", "1")).statusCode()).isEqualTo(400);

        HttpResponse<String> inactiveUnit = price(admin, list, "bulto", "10");
        assertThat(inactiveUnit.statusCode()).isEqualTo(404);
        assertThat(read(inactiveUnit).get("code").asText()).isEqualTo("PRODUCT_UNIT_NOT_FOUND");
        assertThat(admin.post("/catalog/price-lists/" + list + "/prices",
            Map.of("product", UUID.randomUUID().toString(), "unit", "caja", "price", "1")).statusCode()).isEqualTo(404);
        assertThat(admin.post("/catalog/price-lists/" + UUID.randomUUID() + "/prices",
            Map.of("product", product.toString(), "unit", "caja", "price", "1")).statusCode()).isEqualTo(404);

        jdbc.update("UPDATE catalog_product SET active=false WHERE external_id=?", product);
        HttpResponse<String> inactiveProduct = price(admin, list, "unidad", "10");
        assertThat(inactiveProduct.statusCode()).isEqualTo(409);
        assertThat(read(inactiveProduct).get("code").asText()).isEqualTo("PRODUCT_INACTIVE");
        // The existing price is kept and shown with the product's state.
        HttpResponse<String> detail = admin.get("/catalog/price-lists/" + list);
        assertThat(prices(admin, list)).containsExactly("caja=100");
        assertThat(read(detail).get("prices").get(0).get("productActive").asBoolean()).isFalse();
    }

    @Test void pricesAreNeverDeletedByTheRuntimeRole() {
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'catalog_price_entry','DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'catalog_price_list','DELETE')", Boolean.class)).isFalse();
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
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf.has("headerName")) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
