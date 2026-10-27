package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A local database seeded with demonstration data (V100) must accept structural migrations added later with a lower
 * version, such as V8, instead of refusing to start.
 */
@Testcontainers
class DemoMigrationOrderTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");

    @Test void demoDatabaseCreatedBeforeANewMigrationStillStartsAndAppliesIt(@TempDir Path previous) throws Exception {
        // The schema as it was before V8: V1–V7 plus the demonstration seed.
        for (String name : List.of("V1__identity.sql", "V2__catalog_and_commercial.sql", "V3__transfers_and_logistics.sql",
                "V4__inventory_and_idempotency.sql", "V5__spring_session_4_1_1.sql", "V6__query_indexes.sql",
                "V7__runtime_permissions.sql")) {
            copy("db/migration/" + name, previous.resolve(name));
        }
        copy("db/demo/true/V100__local_demonstration.sql", previous.resolve("V100__local_demonstration.sql"));
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:" + previous).load().migrate();
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
            "optiplant_test_runtime", "test-runtime-not-for-deployment"));
        assertThat(db.queryForObject("SELECT has_table_privilege(current_user,'inventory_movement','UPDATE')", Boolean.class)).isTrue();

        try (var context = new SpringApplication(InventoryApplication.class).run("--server.port=0", "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(), "--spring.datasource.username=optiplant_test_runtime",
                "--spring.datasource.password=test-runtime-not-for-deployment",
                "--spring.flyway.user=" + POSTGRES.getUsername(), "--spring.flyway.password=" + POSTGRES.getPassword(),
                "--app.demo-data=true", "--app.admin-code=demo-order-admin", "--app.admin-password=initial individual test password")) {
            assertThat(context.isActive()).isTrue();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='8' AND success", Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT has_table_privilege(current_user,'inventory_movement','UPDATE')", Boolean.class)).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_operation", Long.class)).isEqualTo(2);
    }

    private static void copy(String resource, Path target) throws Exception {
        try (InputStream source = DemoMigrationOrderTest.class.getClassLoader().getResourceAsStream(resource)) {
            Files.copy(source, target);
        }
    }
}
