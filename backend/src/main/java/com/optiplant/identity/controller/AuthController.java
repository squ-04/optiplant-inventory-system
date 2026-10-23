package com.optiplant.identity.controller;

import com.optiplant.identity.dto.LoginRequest;
import com.optiplant.identity.dto.PasswordRequest;
import com.optiplant.identity.dto.UserResponse;
import com.optiplant.identity.repository.UserAccountRepository;
import com.optiplant.identity.service.AccountService;
import com.optiplant.identity.service.LoginService;
import com.optiplant.identity.service.SessionPolicyFilter;
import com.optiplant.identity.service.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LoginService login;
    private final AccountService accounts;
    private final UserAccountRepository users;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final Clock clock;

    public AuthController(LoginService login, AccountService accounts, UserAccountRepository users,
        SecurityContextRepository contexts, CsrfTokenRepository csrf, Clock clock) {
        this.login = login;
        this.accounts = accounts;
        this.users = users;
        this.contexts = contexts;
        this.csrf = csrf;
        this.clock = clock;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
        HttpServletResponse response) {
        var user = login.login(body.code(), body.password());
        // A successful login replaces all pre-authentication session state.
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        var session = request.getSession(true);
        session.setAttribute(SessionPolicyFilter.STARTED_AT, clock.instant());
        session.setAttribute(SessionPolicyFilter.INTERACTIVE_AT, clock.instant());
        var principal = new SessionPrincipal(user.getExternalId(), user.getCredentialsVersion(), user.getRole(),
            accounts.branchExternalId(user));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return accounts.response(user);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SessionPrincipal principal) {
        return accounts.response(users.findByExternalId(principal.externalId()).orElseThrow(AccountService::unauthorized));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal SessionPrincipal principal, @Valid @RequestBody PasswordRequest body,
        HttpServletRequest request, HttpServletResponse response) {
        accounts.changePassword(principal, body.currentPassword(), body.newPassword());
        // All sessions are rejected by credentials_version; this session is removed now.
        invalidate(request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) { invalidate(request, response); }

    private void invalidate(HttpServletRequest request, HttpServletResponse response) {
        csrf.saveToken(null, request, response);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }

    public record CsrfResponse(String token, String headerName) {}
}
