package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.RuleDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<RuleDefinition, UUID> {

    List<RuleDefinition> findByEnabledTrue();
}
