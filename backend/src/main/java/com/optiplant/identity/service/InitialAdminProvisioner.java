package com.optiplant.identity.service;

import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminProvisioner implements ApplicationRunner {
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String code;
    private final String password;

    public InitialAdminProvisioner(UserAccountRepository users, JdbcTemplate jdbc, PasswordEncoder passwords,
        Clock clock, @Value("${app.admin-code}") String code, @Value("${app.admin-password}") String password) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
        this.code = code;
        this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT has_table_privilege(current_user, 'catalog_product', 'DELETE')", Boolean.class))) {
            throw new IllegalStateException("El rol de ejecución no debe disponer de DELETE sobre catalog_product.");
        }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(6420713001)", Object.class);
        boolean existing = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM identity_user_account WHERE role='ADMIN' AND active)", Boolean.class));
        if (existing) return;
        if (code.isBlank() || code.length() > 64 || !code.equals(code.strip())
            || password.length() < 15 || password.length() > 128) {
            throw new IllegalStateException("Configure APP_ADMIN_CODE y APP_ADMIN_PASSWORD válidos para la provisión inicial.");
        }
        if (users.findByCode(code).isPresent()) {
            throw new IllegalStateException("El código de provisión inicial ya está reservado.");
        }
        users.saveAndFlush(UserAccount.initialAdmin(code, passwords.encode(password), clock.instant()));
    }
}
