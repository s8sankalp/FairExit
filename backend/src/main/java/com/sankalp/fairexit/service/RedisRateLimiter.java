package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.RateLimitProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/** Hand written token bucket on Redis (no Bucket4j), one bucket per key. */
@Service
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public record Result(boolean allowed, long remaining, long retryAfterMs) {
    }

    public Result tryConsume(String key) {
        long periodMs = properties.period().toMillis();
        double tokensPerMs = (double) properties.capacity() / periodMs;
        try {
            List<?> reply = redis.execute(SCRIPT, List.of(key),
                    String.valueOf(properties.capacity()),
                    String.valueOf(tokensPerMs),
                    String.valueOf(periodMs));
            return new Result(toLong(reply.get(0)) == 1, toLong(reply.get(1)), toLong(reply.get(2)));
        } catch (DataAccessException e) {
            // Fail open: a Redis outage should slow nobody down or take the API offline
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return new Result(true, properties.capacity(), 0);
        }
    }

    public int capacity() {
        return properties.capacity();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }
}
