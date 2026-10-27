package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationLifecycleTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");

    @Test @Order(1)
    void absentInitialCredentialStopsReadinessWithoutCreatingAdministrator() {
        AtomicBoolean ready = new AtomicBoolean();
        assertThatThrownBy(() -> start(false, "", ready, ""))
            .hasStackTraceContaining("Configure APP_ADMIN_CODE y APP_ADMIN_PASSWORD");
        assertThat(ready).isFalse();
        assertThat(database().queryForObject("SELECT count(*) FROM identity_user_account WHERE role='ADMIN'", Long.class)).isZero();
        assertThat(database().queryForObject("SELECT count(*) FROM identity_branch", Long.class)).isZero();
    }

    @Test @Order(2)
    void failedMigrationStopsReadinessAndDoesNotProvisionAccount() {
        AtomicBoolean ready = new AtomicBoolean();
        assertThatThrownBy(() -> start(true, "classpath:db/broken", ready, "initial individual test password"))
            .hasStackTraceContaining("optiplant_deliberately_missing_relation");
        assertThat(ready).isFalse();
        assertThat(database().queryForObject("SELECT count(*) FROM identity_user_account WHERE role='ADMIN'", Long.class)).isZero();
        assertThat(database().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='999'", Long.class)).isZero();
    }

    @Test @Order(3)
    void restartPreservesSeedHistoryAndAdministratorAndValidatesSchema() throws Exception {
        List<String> before;
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        try (var context = start(true, "", new AtomicBoolean(), "initial individual test password")) {
            assertHealth(context);
            int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            var csrf = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            var json = context.getBean(tools.jackson.databind.json.JsonMapper.class);
            var token = json.readTree(csrf.body());
            var login = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"lifecycle-admin\",\"password\":\"initial individual test password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            before = snapshot();
        }
        try (var context = start(true, "", new AtomicBoolean(), "")) {
            assertHealth(context);
            assertThat(snapshot()).isEqualTo(before);
            int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            var me = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/me"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.body()).contains("\"code\":\"lifecycle-admin\"", "\"mustChangePassword\":true");
        }
        assertThat(database().queryForObject("""
            SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'
            AND table_name NOT IN ('spring_session','spring_session_attributes','flyway_schema_history')
            """, Long.class)).isEqualTo(32);
        assertThat(database().queryForObject("SELECT count(*) FROM identity_branch", Long.class)).isEqualTo(2);
        assertThat(database().queryForObject("SELECT count(*) FROM identity_user_account WHERE role='ADMIN'", Long.class)).isEqualTo(1);
        assertThat(database().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class)).isEqualTo(9);
    }

    @Test @Order(4)
    void runtimeHasNoProductDeletionOrDdlPrivilegeAndSeedBooksBalance() {
        var db = database();
        assertThat(db.queryForObject("SELECT current_user", String.class)).isEqualTo("optiplant_test_runtime");
        assertThat(db.queryForObject("SELECT has_table_privilege(current_user,'catalog_product','DELETE')", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT has_table_privilege(current_user,'spring_session','DELETE')", Boolean.class)).isTrue();
        assertThat(db.queryForObject("SELECT has_schema_privilege(current_user,'public','CREATE')", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT has_table_privilege(current_user,'flyway_schema_history','UPDATE')", Boolean.class)).isFalse();
        assertThat(db.queryForObject("""
            SELECT count(*) FROM inventory_balance b WHERE
            b.physical_qty<>(SELECT coalesce(sum(CASE WHEN m.direction='IN' THEN m.base_qty ELSE -m.base_qty END),0)
                FROM inventory_movement m WHERE m.branch_id=b.branch_id AND m.product_id=b.product_id)
            OR b.inventory_value<>(SELECT coalesce(sum(c.amount),0) FROM inventory_valuation_component c
                JOIN inventory_movement m ON m.id=c.movement_id WHERE m.branch_id=b.branch_id AND m.product_id=b.product_id)
            """, Long.class)).isZero();
        assertThat(db.queryForObject("""
            SELECT count(*) FROM inventory_movement m WHERE
            (SELECT count(*) FROM inventory_valuation_component c WHERE c.movement_id=m.id AND c.component_kind='BASE')<>1
            """, Long.class)).isZero();
        assertThat(db.queryForObject("SELECT sum(physical_qty) FROM inventory_balance", java.math.BigDecimal.class))
            .isEqualByComparingTo("8");
        assertThat(db.queryForObject("SELECT sum(inventory_value) FROM inventory_balance", java.math.BigDecimal.class))
            .isEqualByComparingTo("80");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_operation", Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT sum(net_amount) FROM sales_line", java.math.BigDecimal.class))
            .isEqualByComparingTo("30");
    }

    @Test @Order(5)
    void sessionMigrationIsByteIdenticalToSelectedLibraryScript() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (var migration = loader.getResourceAsStream("db/migration/V5__spring_session_4_1_1.sql");
             var official = loader.getResourceAsStream("org/springframework/session/jdbc/schema-postgresql.sql")) {
            assertThat(migration).isNotNull();
            assertThat(official).isNotNull();
            assertThat(new String(migration.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(new String(official.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private List<String> snapshot() {
        return database().queryForList("""
            SELECT 'branch:'||external_id::text FROM identity_branch
            UNION ALL SELECT 'product:'||external_id::text FROM catalog_product
            UNION ALL SELECT 'account:'||external_id::text||':'||password_hash FROM identity_user_account
            UNION ALL SELECT 'operation:'||external_id::text FROM inventory_operation
            UNION ALL SELECT 'movement:'||external_id::text FROM inventory_movement
            ORDER BY 1
            """, String.class);
    }

    private JdbcTemplate database() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
            "optiplant_test_runtime", "test-runtime-not-for-deployment"));
    }

    private ConfigurableApplicationContext start(boolean demo, String extra, AtomicBoolean ready, String password) {
        SpringApplication application = new SpringApplication(InventoryApplication.class);
        application.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> ready.set(true));
        String locations = "classpath:db/migration" + (demo ? ",classpath:db/demo/true" : "")
            + (extra.isEmpty() ? "" : "," + extra);
        return application.run("--server.port=0", "--spring.main.banner-mode=off",
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(), "--spring.datasource.username=optiplant_test_runtime",
            "--spring.datasource.password=test-runtime-not-for-deployment",
            "--spring.flyway.user=" + POSTGRES.getUsername(), "--spring.flyway.password=" + POSTGRES.getPassword(),
            "--spring.flyway.locations=" + locations, "--app.admin-code=lifecycle-admin", "--app.admin-password=" + password);
    }

    private void assertHealth(ConfigurableApplicationContext context) throws Exception {
        int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health")).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
