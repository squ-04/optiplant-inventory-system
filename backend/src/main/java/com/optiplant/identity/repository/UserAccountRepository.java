package com.optiplant.identity.repository;

import com.optiplant.identity.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByCode(String code);
    Optional<UserAccount> findByExternalId(UUID externalId);
    // Codes are unique regardless of letter case (V12); login still matches the code exactly.
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndExternalIdNot(String code, UUID externalId);
    List<UserAccount> findAllByOrderByCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.externalId = :externalId")
    Optional<UserAccount> lockByExternalId(@Param("externalId") UUID externalId);
}
