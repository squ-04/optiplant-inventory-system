package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.UserResponse;
import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountService(UserAccountRepository users, JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
    }

    public UserResponse response(UserAccount account) {
        return new UserResponse(account.getExternalId(), account.getCode(), account.getRole(),
            branchExternalId(account), account.isMustChangePassword());
    }

    public UUID branchExternalId(UserAccount account) {
        if (account.getBranchId() == null) return null;
        return jdbc.queryForObject("SELECT external_id FROM identity_branch WHERE id = ?", UUID.class,
            account.getBranchId());
    }

    public boolean branchActive(UserAccount account) {
        return account.getBranchId() == null || Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT active FROM identity_branch WHERE id = ?", Boolean.class, account.getBranchId()));
    }

    @Transactional
    public void changePassword(SessionPrincipal principal, String current, String replacement) {
        UserAccount user = users.lockByExternalId(principal.externalId()).orElseThrow(AccountService::unauthorized);
        if (!valid(principal, user)) throw unauthorized();
        if (!passwords.matches(current, user.getPasswordHash())) {
            throw new ApiException(400, "CURRENT_PASSWORD_INVALID", "La contraseña vigente no es válida.");
        }
        user.changePassword(passwords.encode(replacement), clock.instant());
    }

    public boolean valid(SessionPrincipal principal, UserAccount user) {
        return user.isActive() && branchActive(user)
            && principal.credentialsVersion() == user.getCredentialsVersion()
            && principal.role().equals(user.getRole())
            && Objects.equals(principal.branchExternalId(), branchExternalId(user));
    }

    public static ApiException unauthorized() {
        return new ApiException(401, "AUTHENTICATION_REQUIRED", "Debe autenticarse de nuevo.");
    }
}
