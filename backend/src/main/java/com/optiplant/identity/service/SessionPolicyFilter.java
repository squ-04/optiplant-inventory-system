package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiErrors;
import com.optiplant.identity.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

public class SessionPolicyFilter extends OncePerRequestFilter {
    public static final String STARTED_AT = "optiplant.startedAt";
    public static final String INTERACTIVE_AT = "optiplant.interactiveAt";
    // Business resources whose successful POST is a user command; polling only uses GET.
    private static final List<String> COMMANDS = List.of("/api/v1/users", "/api/v1/branches", "/api/v1/products",
        "/api/v1/catalog/price-lists", "/api/v1/inventory/movements", "/api/v1/purchases/suppliers",
        "/api/v1/purchases/orders", "/api/v1/sales", "/api/v1/transfers", "/api/v1/logistics/routes");
    private final UserAccountRepository users;
    private final AccountService accounts;
    private final Clock clock;
    private final JsonMapper json;

    public SessionPolicyFilter(UserAccountRepository users, AccountService accounts, Clock clock, JsonMapper json) {
        this.users = users;
        this.accounts = accounts;
        this.clock = clock;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        Instant now = clock.instant();
        boolean expired = session == null
            || !(session.getAttribute(STARTED_AT) instanceof Instant started)
            || !(session.getAttribute(INTERACTIVE_AT) instanceof Instant interactive)
            || !now.isBefore(started.plus(Duration.ofHours(8)))
            || !now.isBefore(interactive.plus(Duration.ofMinutes(30)));
        var user = users.findByExternalId(principal.externalId()).orElse(null);
        if (expired || user == null || !accounts.valid(principal, user)) {
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            ApiErrors.write(response, json, 401, expired ? "SESSION_EXPIRED" : "SESSION_REVOKED",
                "La sesión ya no es válida. Debe autenticarse de nuevo.");
            return;
        }
        String path = request.getRequestURI();
        if (user.isMustChangePassword() && path.startsWith("/api/") && !path.startsWith("/api/v1/auth/")) {
            ApiErrors.write(response, json, 403, "PASSWORD_CHANGE_REQUIRED", "Debe cambiar su contraseña antes de operar.");
            return;
        }
        // GET (including me, csrf and bootstrap) is never interactive activity.
        // Only this explicit server-side command allowlist can update the timestamp.
        if (request.getMethod().equals("POST") && path.equals("/api/v1/auth/password")) {
            session.setAttribute(INTERACTIVE_AT, now);
        }
        chain.doFilter(request, response);
        if (request.getMethod().equals("POST") && response.getStatus() >= 200 && response.getStatus() < 300
            && command(path)) {
            HttpSession live = request.getSession(false);
            if (live != null) live.setAttribute(INTERACTIVE_AT, now);
        }
    }

    private static boolean command(String path) {
        return COMMANDS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }
}
