package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserAccountRepository users;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String dummyHash;

    public LoginService(UserAccountRepository users, AccountService accounts, JdbcTemplate jdbc,
                        PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    // Rejected attempts must commit their guard state, never account changes.
    @Transactional(noRollbackFor = ApiException.class)
    public UserAccount login(String code, String password) {
        // PostgreSQL stores microseconds; never round a deadline beyond the server clock.
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        byte[] subject = hash(code);
        jdbc.update("""
            INSERT INTO identity_login_guard(subject_hash, window_started_at)
            VALUES (?, ?) ON CONFLICT(subject_hash) DO NOTHING
            """, subject, OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
        Guard guard = jdbc.queryForObject("""
            SELECT window_started_at, failure_count, escalation_level, blocked_until
            FROM identity_login_guard WHERE subject_hash = ? FOR UPDATE
            """, (row, index) -> new Guard(row.getObject(1, OffsetDateTime.class).toInstant(), row.getInt(2),
                row.getInt(3), row.getObject(4, OffsetDateTime.class)), subject);
        if (guard.blockedUntil() != null && guard.blockedUntil().toInstant().isAfter(now)) {
            throw new ApiException(429, "LOGIN_THROTTLED", "Espere antes de volver a intentar el acceso.");
        }
        UserAccount user = users.findByCode(code).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user != null && matches && user.isActive()) {
            jdbc.update("""
                UPDATE identity_login_guard SET failure_count=0, escalation_level=0,
                blocked_until=NULL, window_started_at=?, updated_at=? WHERE subject_hash=?
                """, timestamp(now), timestamp(now), subject);
            return user;
        }
        int failures = Duration.between(guard.startedAt(), now).compareTo(Duration.ofMinutes(15)) >= 0
            ? 1 : guard.failures() + 1;
        Instant started = failures == 1 ? now : guard.startedAt();
        int level = guard.level();
        Instant blocked = null;
        if (failures >= 5) {
            level = Math.min(3, level + 1);
            blocked = now.plus(Duration.ofMinutes(level == 1 ? 5 : level == 2 ? 15 : 30));
            failures = 0;
            started = now;
        }
        jdbc.update("""
            UPDATE identity_login_guard SET failure_count=?, escalation_level=?, blocked_until=?,
            window_started_at=?, updated_at=? WHERE subject_hash=?
            """, failures, level, blocked == null ? null : timestamp(blocked), timestamp(started), timestamp(now), subject);
        throw new ApiException(401, "LOGIN_INVALID", "Código o contraseña no válidos.");
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private static byte[] hash(String code) {
        try {
            // Exact identifiers: never lowercase or trim into a different account.
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }

    private record Guard(Instant startedAt, int failures, int level, OffsetDateTime blockedUntil) {}
}
