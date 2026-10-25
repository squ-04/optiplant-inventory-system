package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.optiplant.identity.entity.UserAccount;
import com.optiplant.identity.repository.UserAccountRepository;
import com.optiplant.identity.service.AccountService;
import com.optiplant.identity.service.SessionPolicyFilter;
import com.optiplant.identity.service.SessionPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class SessionActivityTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void successfulAdministrationRefreshesActivityAndQueriesDoNotAlterTheExistingPolicy() throws Exception {
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        Instant previous = now.minusSeconds(1200);
        var users = mock(UserAccountRepository.class);
        var accounts = mock(AccountService.class);
        var user = mock(UserAccount.class);
        when(users.findByExternalId(any())).thenReturn(Optional.of(user));
        when(accounts.valid(any(), any())).thenReturn(true);
        var principal = new SessionPrincipal(UUID.randomUUID(), 0, "ADMIN", null);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var filter = new SessionPolicyFilter(users, accounts, Clock.fixed(now, ZoneOffset.UTC), JsonMapper.builder().build());
        Object[][] cases = {
            {"POST", "/api/v1/users", 201, now},
            {"POST", "/api/v1/branches/" + UUID.randomUUID() + "/update", 200, now},
            {"POST", "/api/v1/users", 403, previous},
            {"POST", "/api/v1/auth/password", 400, now},
            {"GET", "/api/v1/visibility/branches", 200, previous},
            {"GET", "/api/v1/auth/me", 200, previous},
            {"POST", "/api/v1/not-a-command", 200, previous},
        };
        for (Object[] value : cases) {
            var session = new MockHttpSession();
            session.setAttribute(SessionPolicyFilter.STARTED_AT, now.minusSeconds(1800));
            session.setAttribute(SessionPolicyFilter.INTERACTIVE_AT, previous);
            var request = new MockHttpServletRequest((String) value[0], (String) value[1]);
            request.setSession(session);
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus((Integer) value[2]));
            assertThat(session.getAttribute(SessionPolicyFilter.INTERACTIVE_AT)).as(value[0] + " " + value[1] + " " + value[2]).isEqualTo(value[3]);
        }
    }
}
