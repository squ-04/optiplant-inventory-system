package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V12 makes codes unique regardless of letter case on a database that already holds data. Codes that would collide are
 * never renamed or merged by the migration: it stops before changing anything and names them, and once they are renamed
 * it applies.
 */
@Testcontainers
class CaseInsensitiveCodeMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");

    @Test
    void upgradeStopsOnCollidingCodesWithoutChangesAndAppliesOnceTheyAreRenamed() {
        flyway("11").migrate();
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
            POSTGRES.getPassword()));
        branch(db, "NORTE");
        branch(db, "norte");
        branch(db, "SUR");
        supplier(db, "PRV-1");
        supplier(db, "prv-1");
        List<String> before = db.queryForList("SELECT code FROM identity_branch UNION ALL SELECT code FROM purchases_supplier "
            + "ORDER BY 1", String.class);

        assertThatThrownBy(() -> flyway(null).migrate())
            .hasStackTraceContaining("identity_branch: NORTE, norte")
            .hasStackTraceContaining("purchases_supplier: PRV-1, prv-1")
            .hasStackTraceContaining("No se modificó ningún dato");
        assertThat(db.queryForList("SELECT code FROM identity_branch UNION ALL SELECT code FROM purchases_supplier "
            + "ORDER BY 1", String.class)).isEqualTo(before);
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='12' AND success",
            Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname LIKE '%_ci'", Long.class)).isZero();

        // The responsible renames the collisions; the migration never decides which record keeps the code.
        db.update("DELETE FROM flyway_schema_history WHERE version='12'");
        db.update("UPDATE identity_branch SET code='NORTE-2' WHERE code='norte'");
        db.update("UPDATE purchases_supplier SET code='PRV-2' WHERE code='prv-1'");
        flyway(null).migrate();

        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='12' AND success",
            Long.class)).isEqualTo(1);
        assertThat(db.queryForList("SELECT code FROM identity_branch ORDER BY 1", String.class))
            .containsExactly("NORTE", "NORTE-2", "SUR");
        assertThatThrownBy(() -> branch(db, "Sur")).isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining("uq_identity_branch_code_ci");
        assertThatThrownBy(() -> supplier(db, "prv-2")).isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining("uq_purchases_supplier_code_ci");
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static void branch(JdbcTemplate db, String code) {
        db.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), code, "Sucursal " + code);
    }

    private static void supplier(JdbcTemplate db, String code) {
        db.update("INSERT INTO purchases_supplier(external_id,code,name) VALUES (?,?,?)", UUID.randomUUID(), code, "Proveedor " + code);
    }
}
