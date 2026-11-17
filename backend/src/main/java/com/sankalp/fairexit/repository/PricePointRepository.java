package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.PricePoint;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PricePointRepository extends JpaRepository<PricePoint, UUID> {

    /** Sorted by group, so the nightly job can process one group at a time while streaming. */
    @Query("select p from PricePoint p order by p.category, p.city, p.flatSize")
    Stream<PricePoint> streamAllByGroup();
}
