package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.EvidencePack;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidencePackRepository extends JpaRepository<EvidencePack, UUID> {
}
