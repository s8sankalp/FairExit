package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Tenancy;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every lookup takes the user id. There is deliberately no "find by id only" used
 * by the service, so one user can never load another user's tenancy by guessing an id.
 */
public interface TenancyRepository extends JpaRepository<Tenancy, UUID> {

    List<Tenancy> findByUserIdOrderByStartDateDesc(UUID userId);

    Optional<Tenancy> findByIdAndUserId(UUID id, UUID userId);

    /** Same, with SELECT ... FOR UPDATE: appends to the hash chain go one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenancy t where t.id = :id and t.userId = :userId")
    Optional<Tenancy> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
