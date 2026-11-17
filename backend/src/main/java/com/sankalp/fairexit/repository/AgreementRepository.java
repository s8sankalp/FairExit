package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Agreement;
import com.sankalp.fairexit.model.AgreementStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgreementRepository extends JpaRepository<Agreement, UUID> {

    Optional<Agreement> findFirstByTenancyIdOrderByCreatedAtDesc(UUID tenancyId);

    /** The newest agreement that was read successfully: the one the audit uses. */
    Optional<Agreement> findFirstByTenancyIdAndStatusOrderByCreatedAtDesc(UUID tenancyId, AgreementStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agreement a where a.id = :id")
    Optional<Agreement> findByIdForUpdate(@Param("id") UUID id);
}
