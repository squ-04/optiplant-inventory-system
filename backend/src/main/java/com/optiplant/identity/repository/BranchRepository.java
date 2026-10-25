package com.optiplant.identity.repository;

import com.optiplant.identity.entity.Branch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findAllByOrderByCodeAsc();
    Optional<Branch> findByExternalId(UUID externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.externalId = :externalId")
    Optional<Branch> lockByExternalId(@Param("externalId") UUID externalId);
}
