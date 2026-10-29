package com.optiplant.purchases.repository;

import com.optiplant.purchases.entity.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByOrderByCodeAsc();
    Optional<Supplier> findByExternalId(UUID externalId);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
