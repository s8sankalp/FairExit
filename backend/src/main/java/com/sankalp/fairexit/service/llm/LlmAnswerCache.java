package com.sankalp.fairexit.service.llm;

import com.sankalp.fairexit.config.LlmProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Remembers LLM answers that passed our checks, keyed by a SHA-256 of the input, so the
 * same message or file is never sent to the LLM twice. Only checked answers are stored.
 * A cache problem is never an error: a miss just means one more LLM call.
 */
@Component
public class LlmAnswerCache {

    private static final Logger log = LoggerFactory.getLogger(LlmAnswerCache.class);
    private static final String PREFIX = "llm:answer:";

    private final StringRedisTemplate redis;
    private final LlmProperties properties;

    public LlmAnswerCache(StringRedisTemplate redis, LlmProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + key));
        } catch (DataAccessException e) {
            log.warn("LLM cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, String answer) {
        try {
            redis.opsForValue().set(PREFIX + key, answer, properties.cacheTtl());
        } catch (DataAccessException e) {
            log.warn("LLM cache write failed: {}", e.getMessage());
        }
    }
}
