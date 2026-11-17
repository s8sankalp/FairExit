package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.PriceStats;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PriceStatsRepository extends JpaRepository<PriceStats, UUID> {

    Optional<PriceStats> findByCategoryAndCityAndFlatSize(DeductionCategory category, String city, FlatSize flatSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PriceStats s")
    void deleteAllStats();
}
