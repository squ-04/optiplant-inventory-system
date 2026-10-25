package com.optiplant.identity.service;

import com.optiplant.common.exception.ApiException;
import com.optiplant.identity.dto.BranchCreateRequest;
import com.optiplant.identity.dto.BranchResponse;
import com.optiplant.identity.dto.BranchUpdateRequest;
import com.optiplant.identity.entity.Branch;
import com.optiplant.identity.repository.BranchRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {
    private final BranchRepository branches;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BranchService(BranchRepository branches, JdbcTemplate jdbc, Clock clock) {
        this.branches = branches;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<BranchResponse> list() {
        return branches.findAllByOrderByCodeAsc().stream().map(this::response).toList();
    }

    public BranchResponse find(UUID externalId) {
        return response(branches.findByExternalId(externalId).orElseThrow(BranchService::notFound));
    }

    @Transactional
    public BranchResponse create(BranchCreateRequest request) {
        try {
            return response(branches.saveAndFlush(Branch.create(request.code(), request.name(), clock.instant())));
        } catch (DataIntegrityViolationException error) {
            throw new ApiException(409, "BRANCH_CODE_EXISTS", "El código de sucursal ya está registrado.");
        }
    }

    @Transactional
    public BranchResponse update(UUID externalId, BranchUpdateRequest request) {
        Branch branch = locked(externalId);
        branch.rename(request.name(), clock.instant());
        return response(branch);
    }

    @Transactional
    public BranchResponse deactivate(UUID externalId) {
        Branch branch = locked(externalId);
        if (!branch.isActive()) return response(branch);
        if (exists("SELECT EXISTS (SELECT 1 FROM inventory_balance WHERE branch_id=? AND physical_qty<>0)", branch.getId())) {
            throw blocked("La sucursal tiene existencias físicas distintas de cero.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM purchases_order WHERE branch_id=? AND status IN ('DRAFT','PENDING'))", branch.getId())) {
            throw blocked("La sucursal tiene compras en borrador o pendientes de recepción.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM transfers_transfer WHERE (origin_branch_id=? OR destination_branch_id=?) "
            + "AND status IN ('REQUESTED','PREPARING','APPROVED','IN_TRANSIT'))", branch.getId(), branch.getId())) {
            throw blocked("La sucursal participa en transferencias abiertas.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM inventory_transit i JOIN logistics_dispatch_line dl ON dl.id=i.dispatch_line_id "
            + "JOIN logistics_dispatch d ON d.id=dl.dispatch_id JOIN transfers_transfer t ON t.id=d.transfer_id "
            + "WHERE (t.origin_branch_id=? OR t.destination_branch_id=?) AND "
            + "(i.pending_base_qty<>0 OR i.pending_base_value<>0 OR i.pending_rounding_value<>0))", branch.getId(), branch.getId())) {
            throw blocked("La sucursal tiene inventario en tránsito pendiente.");
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM logistics_discrepancy ld JOIN logistics_arrival la ON la.id=ld.arrival_id "
            + "JOIN logistics_dispatch d ON d.id=la.dispatch_id JOIN transfers_transfer t ON t.id=d.transfer_id "
            + "WHERE ld.status='OPEN' AND (t.origin_branch_id=? OR t.destination_branch_id=?))", branch.getId(), branch.getId())) {
            throw blocked("La sucursal tiene faltantes de transferencia pendientes de tratamiento.");
        }
        branch.deactivate(clock.instant());
        return response(branch);
    }

    private Branch locked(UUID externalId) {
        return branches.lockByExternalId(externalId).orElseThrow(BranchService::notFound);
    }

    private boolean exists(String sql, Object... values) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, values));
    }

    private BranchResponse response(Branch branch) {
        return new BranchResponse(branch.getExternalId(), branch.getCode(), branch.getName(), branch.isActive());
    }

    private static ApiException notFound() {
        return new ApiException(404, "BRANCH_NOT_FOUND", "La sucursal no existe.");
    }

    private static ApiException blocked(String detail) {
        return new ApiException(409, "BRANCH_DEACTIVATION_BLOCKED", detail);
    }
}
