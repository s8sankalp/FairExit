package com.sankalp.fairexit.repository;

import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByTenancyIdOrderByDueAtAsc(UUID tenancyId);

    List<Reminder> findByStatus(ReminderStatus status);

    /**
     * The one write that makes sending count: PENDING -> SENT, only if still PENDING.
     * Returns 1 for the caller that won, 0 for anyone else. No second SENT can happen.
     */
    // Its own short transaction: the dispatcher calls it with no transaction open
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Reminder r set r.status = com.sankalp.fairexit.model.ReminderStatus.SENT, r.sentAt = :now,
                   r.attempts = r.attempts + 1, r.lastError = null
            where r.id = :id and r.status = com.sankalp.fairexit.model.ReminderStatus.PENDING
            """)
    int markSent(@Param("id") UUID id, @Param("now") Instant now);

    // Its own short transaction: the dispatcher calls it with no transaction open
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Reminder r set r.attempts = r.attempts + 1, r.lastError = :error
            where r.id = :id and r.status = com.sankalp.fairexit.model.ReminderStatus.PENDING
            """)
    int recordFailure(@Param("id") UUID id, @Param("error") String error);

    // Its own short transaction: the dispatcher calls it with no transaction open
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Reminder r set r.status = com.sankalp.fairexit.model.ReminderStatus.CANCELLED
            where r.tenancyId = :tenancyId and r.status = com.sankalp.fairexit.model.ReminderStatus.PENDING
            """)
    int cancelPending(@Param("tenancyId") UUID tenancyId);
}
