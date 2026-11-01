package com.optiplant.catalog.repository;

import com.optiplant.catalog.entity.PriceList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence of price lists, addressed publicly by external id. The case-insensitive checks support code uniqueness
 * on creation and on update of another list.
 */
public interface PriceListRepository extends JpaRepository<PriceList, Long> {
    List<PriceList> findAllByOrderByCodeAsc();
    List<PriceList> findAllByActiveTrueOrderByCodeAsc();
    Optional<PriceList> findByExternalId(UUID externalId);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
