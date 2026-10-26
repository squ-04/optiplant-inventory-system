package com.optiplant.catalog.controller;

import com.optiplant.catalog.dto.ProductCreateRequest;
import com.optiplant.catalog.dto.ProductEnablementRequest;
import com.optiplant.catalog.dto.ProductResponse;
import com.optiplant.catalog.dto.ProductUpdateRequest;
import com.optiplant.catalog.service.ProductService;
import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.EmptyCommandBody;
import com.optiplant.identity.service.SessionPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Common network catalog. There is intentionally no DELETE route: products are only deactivated. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private static final Set<String> READERS = Set.of("ADMIN", "MANAGER", "OPERATOR");
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @GetMapping
    public List<ProductResponse> list(@AuthenticationPrincipal SessionPrincipal principal) {
        requireReader(principal);
        return products.list();
    }

    @GetMapping("/{externalId}")
    public ProductResponse find(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId) {
        requireReader(principal);
        return products.find(externalId, admin(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal SessionPrincipal principal, @RequestBody JsonNode body) {
        requireAdmin(principal);
        return products.create(ProductCreateRequest.from(body));
    }

    @PostMapping("/{externalId}/update")
    public ProductResponse update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody JsonNode body) {
        requireAdmin(principal);
        return products.update(externalId, ProductUpdateRequest.from(body));
    }

    @PostMapping("/{externalId}/branches")
    public ProductResponse enable(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody JsonNode body) {
        requireAdmin(principal);
        return products.enable(externalId, ProductEnablementRequest.from(body));
    }

    @PostMapping("/{externalId}/deactivate")
    public ProductResponse deactivate(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody(required = false) JsonNode body) {
        requireAdmin(principal);
        EmptyCommandBody.validate(body);
        return products.deactivate(externalId);
    }

    @PostMapping("/{externalId}/reactivate")
    public ProductResponse reactivate(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID externalId,
        @RequestBody(required = false) JsonNode body) {
        requireAdmin(principal);
        EmptyCommandBody.validate(body);
        return products.reactivate(externalId);
    }

    private static void requireReader(SessionPrincipal principal) {
        if (!READERS.contains(principal.role())) throw roleForbidden();
    }

    private static void requireAdmin(SessionPrincipal principal) {
        if (!admin(principal)) throw roleForbidden();
    }

    private static boolean admin(SessionPrincipal principal) { return "ADMIN".equals(principal.role()); }

    private static ApiException roleForbidden() {
        return new ApiException(403, "ROLE_FORBIDDEN", "El rol no permite mantener el catálogo de productos.");
    }
}
