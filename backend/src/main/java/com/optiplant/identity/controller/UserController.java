package com.optiplant.identity.controller;

import com.optiplant.common.exception.ApiErrors;
import com.optiplant.identity.dto.ManagedUserCreateRequest;
import com.optiplant.identity.dto.ManagedUserResponse;
import com.optiplant.identity.dto.ManagedUserUpdateRequest;
import com.optiplant.identity.dto.EmptyCommandBody;
import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.identity.service.UserManagementService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserManagementService users;

    public UserController(UserManagementService users) { this.users = users; }

    @GetMapping
    public List<ManagedUserResponse> list(@AuthenticationPrincipal SessionPrincipal principal) {
        return users.list(principal);
    }

    @GetMapping("/{externalId}")
    public ManagedUserResponse get(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId) {
        return users.get(principal, externalId);
    }

    @PostMapping
    public ResponseEntity<ManagedUserResponse> create(@AuthenticationPrincipal SessionPrincipal principal,
        @RequestBody ManagedUserCreateRequest request) {
        ManagedUserResponse response = users.create(principal, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.externalId())).body(response);
    }

    @PostMapping("/{externalId}/update")
    public ManagedUserResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody ManagedUserUpdateRequest request) {
        return users.update(principal, externalId, request);
    }

    @PostMapping("/{externalId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody(required = false) JsonNode body) {
        EmptyCommandBody.validate(body);
        users.deactivate(principal, externalId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> duplicateCode(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrors.problem(409, "USER_CODE_CONFLICT",
            "El código de usuario ya está registrado."));
    }
}
