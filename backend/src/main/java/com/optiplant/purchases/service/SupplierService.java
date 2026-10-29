package com.optiplant.purchases.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.purchases.dto.SupplierRequest;
import com.optiplant.purchases.dto.SupplierResponse;
import com.optiplant.purchases.entity.Supplier;
import com.optiplant.purchases.repository.SupplierRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance of the minimum supplier reference (RF-0021-CA-02). A duplicated code leaves the catalog unchanged. */
@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final Clock clock;

    public SupplierService(SupplierRepository suppliers, Clock clock) {
        this.suppliers = suppliers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return suppliers.findAllByOrderByCodeAsc().stream().map(SupplierService::response).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (suppliers.existsByCodeIgnoreCase(request.code())) throw codeExists();
        try {
            return response(suppliers.saveAndFlush(Supplier.create(request.code(), request.name(), clock.instant())));
        } catch (DataIntegrityViolationException error) {
            throw codeExists();
        }
    }

    @Transactional
    public SupplierResponse update(UUID externalId, SupplierRequest request) {
        Supplier supplier = suppliers.findByExternalId(externalId)
            .orElseThrow(() -> new ApiException(404, "SUPPLIER_NOT_FOUND", "No se encontró el proveedor."));
        if (suppliers.existsByCodeIgnoreCaseAndIdNot(request.code(), supplier.getId())) throw codeExists();
        supplier.update(request.code(), request.name(), clock.instant());
        try {
            return response(suppliers.saveAndFlush(supplier));
        } catch (DataIntegrityViolationException error) {
            throw codeExists();
        }
    }

    static SupplierResponse response(Supplier supplier) {
        return new SupplierResponse(supplier.getExternalId(), supplier.getCode(), supplier.getName(), supplier.isActive());
    }

    private static ApiException codeExists() {
        return new ApiException(409, "SUPPLIER_CODE_EXISTS", "Ya existe un proveedor con ese código. No se modificó el catálogo.");
    }
}
