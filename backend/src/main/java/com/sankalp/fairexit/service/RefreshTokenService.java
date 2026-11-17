package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.JwtProperties;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens are random strings, not JWTs, kept in Redis so they can be revoked.
 * Redis stores only a SHA-256 of the token: a leaked Redis dump gives no usable tokens.
 * Each token works once (rotation): refreshing deletes the old one and issues a new one.
 * A per user set of token hashes lets account deletion revoke all of them at once.
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USER_PREFIX = "refresh-user:";

    private final StringRedisTemplate redis;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = Hashing.sha256Hex(token);
        redis.opsForValue().set(KEY_PREFIX + hash, userId.toString(), properties.refreshTtl());
        redis.opsForSet().add(USER_PREFIX + userId, hash);
        redis.expire(USER_PREFIX + userId, properties.refreshTtl());
        return token;
    }

    /** Uses the token up. GETDEL is atomic, so two parallel refreshes cannot both succeed. */
    public Optional<UUID> consume(String token) {
        String userId = redis.opsForValue().getAndDelete(key(token));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    /** Logs the user out everywhere: every refresh token they hold stops working. */
    public void revokeAll(UUID userId) {
        java.util.Set<String> hashes = redis.opsForSet().members(USER_PREFIX + userId);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(h -> KEY_PREFIX + h).toList());
        }
        redis.delete(USER_PREFIX + userId);
    }

    private static String key(String token) {
        return KEY_PREFIX + Hashing.sha256Hex(token);
    }
}
