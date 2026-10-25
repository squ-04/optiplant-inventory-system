package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import com.optiplant.identity.service.AccountService;
import com.optiplant.identity.service.SessionPrincipal;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountPolicyTest {
    @Test
    void inactiveBranchDoesNotDisableAccountOrRevokeHistoricalReadAccess() {
        var jdbc = mock(JdbcTemplate.class);
        var user = mock(UserAccount.class);
        var branch = UUID.randomUUID();
        when(user.getBranchId()).thenReturn(7L);
        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn("MANAGER");
        when(user.getCredentialsVersion()).thenReturn(2L);
        when(jdbc.queryForObject("SELECT external_id FROM identity_branch WHERE id = ?", UUID.class, 7L)).thenReturn(branch);
        when(jdbc.queryForObject("SELECT active FROM identity_branch WHERE id = ?", Boolean.class, 7L)).thenReturn(false);
        var service = new AccountService(mock(UserAccountRepository.class), jdbc, mock(PasswordEncoder.class), Clock.systemUTC());
        var principal = new SessionPrincipal(UUID.randomUUID(), 2L, "MANAGER", branch);
        assertThat(service.branchActive(user)).isFalse();
        assertThat(service.valid(principal, user)).isTrue();
        when(user.isActive()).thenReturn(false);
        assertThat(service.valid(principal, user)).isFalse();
    }
}
