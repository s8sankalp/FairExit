package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.DeductionBill;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeductionBillRepository extends JpaRepository<DeductionBill, UUID> {

    java.util.List<DeductionBill> findByTenancyIdOrderByCreatedAtDesc(UUID tenancyId);

    /** A bill is visible only through a tenancy the user owns. */
    @Query("""
            select b from DeductionBill b
            where b.id = :billId
              and exists (select 1 from Tenancy t where t.id = b.tenancyId and t.userId = :userId)
            """)
    Optional<DeductionBill> findOwned(@Param("billId") UUID billId, @Param("userId") UUID userId);

    /**
     * Same, with a row lock (SELECT ... FOR UPDATE). Used when the tenant edits lines,
     * so the edit and the background extraction cannot both write the bill at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from DeductionBill b
            where b.id = :billId
              and exists (select 1 from Tenancy t where t.id = b.tenancyId and t.userId = :userId)
            """)
    Optional<DeductionBill> findOwnedForUpdate(@Param("billId") UUID billId, @Param("userId") UUID userId);

    /** For the background worker, which acts for the system, not for a user. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DeductionBill b where b.id = :billId")
    Optional<DeductionBill> findByIdForUpdate(@Param("billId") UUID billId);
}
