package com.sankalp.fairexit.service.llm;

import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The one path every LLM call takes: cache, then the user's daily limit, then the call,
 * then our checks. Only an answer that passes the checks is cached.
 */
@Component
public class LlmGateway {

    private final LlmAnswerCache cache;
    private final LlmQuota quota;
    private final FairExitMetrics metrics;

    public LlmGateway(LlmAnswerCache cache, LlmQuota quota, FairExitMetrics metrics) {
        this.cache = cache;
        this.quota = quota;
        this.metrics = metrics;
    }

    public <T> T call(UUID userId, String cacheKey, Supplier<String> llmCall, Function<String, T> check) {
        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            metrics.llmCall("cache_hit");
            return check.apply(cached.get()); // free: no LLM call, no quota used
        }
        if (!quota.tryAcquire(userId)) {
            metrics.llmCall("quota_exceeded");
            throw new LlmException(Kind.QUOTA_EXCEEDED, "Daily LLM limit reached for user " + userId);
        }
        try {
            String answer = llmCall.get();
            T checked = check.apply(answer); // throws on a bad answer, which is then never cached
            cache.put(cacheKey, answer);
            metrics.llmCall("success");
            return checked;
        } catch (LlmException e) {
            metrics.llmCall("error");
            throw e;
        }
    }
}
