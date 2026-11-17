package com.sankalp.fairexit.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * A simple lock across app instances (no Redisson, by design).
 *  lock:   SET key token NX PX ttl   (only one caller can create the key)
 *  unlock: delete the key only if it still holds our token (one atomic Lua script),
 *          so a slow holder whose lock already expired cannot delete someone else's lock.
 * The TTL means a crashed holder never blocks the others for ever.
 */
@Component
public class RedisLock {

    private static final RedisScript<Long> RELEASE = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** The token to unlock with, or empty when someone else holds the lock. */
    public Optional<String> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public boolean unlock(String key, String token) {
        Long deleted = redis.execute(RELEASE, List.of(key), token);
        return deleted != null && deleted == 1;
    }
}
