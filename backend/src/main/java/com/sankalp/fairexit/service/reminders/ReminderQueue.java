package com.sankalp.fairexit.service.reminders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis sorted set of reminder ids, scored by due time in epoch milliseconds.
 * "What is due now?" is one ZRANGEBYSCORE 0..now, however many reminders exist.
 * The set is only an index: losing it loses nothing, because it is rebuilt from PostgreSQL.
 */
@Component
public class ReminderQueue {

    static final String KEY = "reminders:due";

    private final StringRedisTemplate redis;

    public ReminderQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** ZADD is idempotent: adding the same id again just keeps one entry. */
    public void add(UUID reminderId, Instant dueAt) {
        redis.opsForZSet().add(KEY, reminderId.toString(), dueAt.toEpochMilli());
    }

    public List<UUID> due(Instant now, int limit) {
        Set<String> ids = redis.opsForZSet().rangeByScore(KEY, 0, now.toEpochMilli(), 0, limit);
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    public void remove(UUID reminderId) {
        redis.opsForZSet().remove(KEY, reminderId.toString());
    }

    public long size() {
        Long size = redis.opsForZSet().size(KEY);
        return size == null ? 0 : size;
    }
}
