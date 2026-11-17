package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.DocumentStatus;
import com.sankalp.fairexit.model.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndTenancyId(UUID id, UUID tenancyId);

    /** The chain in order. */
    List<Document> findByTenancyIdAndStatusOrderByChainSeqAsc(UUID tenancyId, DocumentStatus status);

    List<Document> findByTenancyIdAndStatusAndType(UUID tenancyId, DocumentStatus status, DocumentType type);

    @Query("""
            select d from Document d
            where d.id = :id
              and exists (select 1 from Tenancy t where t.id = d.tenancyId and t.userId = :userId)
            """)
    Optional<Document> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
