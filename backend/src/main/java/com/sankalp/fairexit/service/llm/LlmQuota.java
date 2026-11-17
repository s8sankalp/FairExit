package com.sankalp.fairexit.service.llm;

import com.sankalp.fairexit.config.LlmProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * At most 20 LLM calls per user per day, to cap cost. One Redis counter per user per
 * day (Indian time). INCR is atomic, so parallel calls cannot slip past the limit.
 * If Redis is down this throws and the job is retried: cost protection fails closed.
 */
@Component
public class LlmQuota {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final StringRedisTemplate redis;
    private final LlmProperties properties;
    private final Clock clock;

    public LlmQuota(StringRedisTemplate redis, LlmProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one call. Returns false when today's limit is already used up. */
    public boolean tryAcquire(UUID userId) {
        String key = "llm:quota:" + userId + ":" + LocalDate.now(clock.withZone(INDIA));
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofDays(2));
        }
        return count != null && count <= properties.dailyLimit();
    }
}
