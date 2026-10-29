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

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderBySkuAsc();
    Optional<Product> findByExternalId(UUID externalId);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.externalId = :externalId")
    Optional<Product> lockByExternalId(@Param("externalId") UUID externalId);
}
