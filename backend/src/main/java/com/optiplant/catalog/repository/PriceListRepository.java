package com.optiplant.catalog.repository;

import com.optiplant.catalog.entity.PriceList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {
    List<PriceList> findAllByOrderByCodeAsc();
    List<PriceList> findAllByActiveTrueOrderByCodeAsc();
    Optional<PriceList> findByExternalId(UUID externalId);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
