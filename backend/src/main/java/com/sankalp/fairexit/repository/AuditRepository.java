package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Audit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRepository extends JpaRepository<Audit, UUID> {

    Optional<Audit> findFirstByBillIdOrderByCreatedAtDesc(UUID billId);

    /** Audit -> bill -> tenancy -> user, in one query. */
    @Query("""
            select a from Audit a
            where a.id = :auditId
              and exists (
                  select 1 from DeductionBill b, Tenancy t
                  where b.id = a.billId and t.id = b.tenancyId and t.userId = :userId)
            """)
    Optional<Audit> findOwned(@Param("auditId") UUID auditId, @Param("userId") UUID userId);
}
