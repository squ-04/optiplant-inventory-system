package com.optiplant.catalog.repository;

import com.optiplant.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence of catalog products, addressed publicly by external id. {@code lockByExternalId} takes a pessimistic write
 * lock for commands that must serialize on one product; SKU checks ignore letter case.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderBySkuAsc();
    Optional<Product> findByExternalId(UUID externalId);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.externalId = :externalId")
    Optional<Product> lockByExternalId(@Param("externalId") UUID externalId);
}
