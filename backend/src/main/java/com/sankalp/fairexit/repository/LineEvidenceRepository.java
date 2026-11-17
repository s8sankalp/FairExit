package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.LineEvidence;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineEvidenceRepository extends JpaRepository<LineEvidence, UUID> {

    List<LineEvidence> findByLineIdIn(Collection<UUID> lineIds);

    List<LineEvidence> findByLineId(UUID lineId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LineEvidence e where e.lineId = :lineId")
    void deleteByLine(@Param("lineId") UUID lineId);
}
