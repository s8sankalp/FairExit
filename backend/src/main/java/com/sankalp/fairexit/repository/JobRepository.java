package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    long countByStatus(JobStatus status);

    /**
     * Locks up to {@code limit} due jobs for the current transaction.
     * SKIP LOCKED makes a second worker skip rows another worker already holds
     * instead of waiting for them, so two workers never take the same job.
     */
    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'PENDING' AND run_at <= :now
            ORDER BY run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Puts jobs back in the queue when their worker died while running them. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :pending, j.lockedAt = null, j.runAt = :now, j.updatedAt = :now
            where j.status = :running and j.lockedAt < :cutoff
            """)
    int requeueStale(@Param("running") JobStatus running, @Param("pending") JobStatus pending,
                     @Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
