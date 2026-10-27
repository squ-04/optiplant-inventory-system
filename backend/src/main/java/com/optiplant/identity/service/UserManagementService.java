package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.ManagedUserCreateRequest;
import com.optiplant.identity.dto.ManagedUserResponse;
import com.optiplant.identity.dto.ManagedUserUpdateRequest;
import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public UserManagementService(UserAccountRepository users, JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ManagedUserResponse> list(SessionPrincipal principal) {
        requireAdmin(principal);
        return users.findAllByOrderByCodeAsc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ManagedUserResponse get(SessionPrincipal principal, UUID externalId) {
        requireAdmin(principal);
        return response(users.findByExternalId(externalId).orElseThrow(UserManagementService::notFound));
    }

    @Transactional
    public ManagedUserResponse create(SessionPrincipal principal, ManagedUserCreateRequest request) {
        requireAdmin(principal);
        Values values = createValues(request);
        rejectDuplicate(values.code(), null);
        UserAccount account = UserAccount.create(values.code(), values.fullName(), values.role(), values.branchId(),
            passwords.encode(request.getTemporaryPassword()), clock.instant());
        return response(users.saveAndFlush(account));
    }

    @Transactional
    public ManagedUserResponse update(SessionPrincipal principal, UUID externalId, ManagedUserUpdateRequest request) {
        requireAdmin(principal);
        UserAccount account = users.lockByExternalId(externalId).orElseThrow(UserManagementService::notFound);
        Values values = updateValues(request);
        rejectDuplicate(values.code(), externalId);
        account.update(values.code(), values.fullName(), values.role(), values.branchId(), clock.instant());
        return response(account);
    }

    @Transactional
    public void deactivate(SessionPrincipal principal, UUID externalId) {
        requireAdmin(principal);
        UserAccount account = users.lockByExternalId(externalId).orElseThrow(UserManagementService::notFound);
        account.deactivate(clock.instant());
    }

    private Values createValues(ManagedUserCreateRequest request) {
        if (request.getTemporaryPassword() == null || request.getTemporaryPassword().length() < 15
            || request.getTemporaryPassword().length() > 128) {
            throw invalid("La contraseña temporal debe tener entre 15 y 128 caracteres.");
        }
        Values values = values(request.getCode(), request.getFullName(), request.getRole(), request.getBranchExternalId());
        if (request.getTemporaryPassword().equals(values.code())) {
            throw invalid("La contraseña temporal debe ser distinta del código de usuario.");
        }
        return values;
    }

    private Values updateValues(ManagedUserUpdateRequest request) {
        return values(request.getCode(), request.getFullName(), request.getRole(), request.getBranchExternalId());
    }

    private Values values(String rawCode, String rawName, String role, UUID branchExternalId) {
        String code = trimmed(rawCode, 64);
        String fullName = trimmed(rawName, 160);
        if (!"ADMIN".equals(role) && !"MANAGER".equals(role) && !"OPERATOR".equals(role)) throw invalid();
        if ("ADMIN".equals(role) && branchExternalId == null) {
            return new Values(code, fullName, role, null);
        }
        if (branchExternalId == null || branchExternalId.version() != 4 || branchExternalId.variant() != 2) throw invalid();
        Long branchId = jdbc.query("SELECT id, active FROM identity_branch WHERE external_id = ? FOR SHARE",
            row -> {
                if (!row.next()) return null;
                return row.getBoolean("active") ? row.getLong("id") : null;
            }, branchExternalId);
        if (branchId == null) throw new ApiException(400, "BRANCH_INVALID", "La sucursal asignada no está activa.");
        return new Values(code, fullName, role, branchId);
    }

    private void rejectDuplicate(String code, UUID externalId) {
        boolean duplicate = externalId == null ? users.findByCode(code).isPresent()
            : users.existsByCodeAndExternalIdNot(code, externalId);
        if (duplicate) throw new ApiException(409, "USER_CODE_CONFLICT", "El código de usuario ya está registrado.");
    }

    private ManagedUserResponse response(UserAccount account) {
        UUID branchExternalId = account.getBranchId() == null ? null : jdbc.queryForObject(
            "SELECT external_id FROM identity_branch WHERE id = ?", UUID.class, account.getBranchId());
        return new ManagedUserResponse(account.getExternalId(), account.getCode(), account.getFullName(), account.getRole(),
            branchExternalId, account.isActive(), account.isMustChangePassword());
    }

    private static String trimmed(String value, int maximum) {
        if (value == null) throw invalid();
        String result = value.trim();
        if (result.isEmpty() || result.length() > maximum) throw invalid();
        return result;
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (principal == null || !"ADMIN".equals(principal.role())) {
            throw new ApiException(403, "ROLE_FORBIDDEN", "El rol no permite administrar usuarios.");
        }
    }

    private static ApiException invalid() {
        return invalid("La solicitud no es válida.");
    }

    private static ApiException invalid(String detail) {
        return new ApiException(400, "VALIDATION_FAILED", detail);
    }

    private static ApiException notFound() {
        return new ApiException(404, "USER_NOT_FOUND", "El usuario no existe.");
    }

    private record Values(String code, String fullName, String role, Long branchId) {}
}
