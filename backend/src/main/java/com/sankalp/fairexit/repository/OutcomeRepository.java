package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Outcome;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutcomeRepository extends JpaRepository<Outcome, UUID> {

    Optional<Outcome> findByTenancyId(UUID tenancyId);
}
