package com.optiplant.dashboard.service;

import com.optiplant.dashboard.dto.VisibilityResponse;
import com.optiplant.dashboard.dto.VisibilityResponse.Branch;
import com.optiplant.dashboard.dto.VisibilityResponse.BranchInventory;
import com.optiplant.dashboard.dto.VisibilityResponse.InventoryItem;
import com.optiplant.dashboard.repository.VisibilityRepository;
import com.optiplant.identity.service.ScopePolicy;
import com.optiplant.identity.service.SessionPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class VisibilityService {
    private final VisibilityRepository data;
    private final ScopePolicy policy;
    public VisibilityService(VisibilityRepository data, ScopePolicy policy) { this.data = data; this.policy = policy; }
    public List<Branch> branches() { return data.branches(); }
    /** One branch, active or not; answers 404 when it does not exist. Authorization is checked by the caller. */
    public Branch branch(UUID branch) { return data.branch(branch); }
    public VisibilityResponse history(SessionPrincipal principal, UUID branch) {
        policy.requirePublicReference(branch);
        policy.requireHistoryRead(principal, branch);
        return new VisibilityResponse(data.branch(branch), data.inventory(branch), data.purchases(branch),
            data.sales(branch), data.transfers(branch), data.movements(branch));
    }
    public List<InventoryItem> inventory(UUID branch) {
        policy.requirePublicReference(branch);
        data.branch(branch);
        // Cross-branch stock consultation does not expose valuation or authorize commands.
        return data.inventory(branch);
    }
    /**
     * RF-0014: any authenticated profile consults the stock of any branch, including inactive ones, without valuation
     * and without gaining write scope over it. RF-0015: clients poll this read to observe confirmed changes.
     */
    public BranchInventory branchInventory(UUID branch) {
        policy.requirePublicReference(branch);
        Branch found = data.branch(branch);
        return new BranchInventory(found, data.snapshotTime(), data.stock(branch));
    }
}
