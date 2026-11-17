package com.sankalp.fairexit.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The app's own metrics (PRD section 10), on top of the HTTP and JVM metrics Spring gives
 * for free. In Prometheus they appear with dots as underscores, for example
 * fairexit_audit_duration_seconds_bucket.
 */
@Component
public class FairExitMetrics {

    private final MeterRegistry registry;
    private final Timer auditTimer;

    public FairExitMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.auditTimer = Timer.builder("fairexit.audit.duration")
                .description("Time the rules engine takes for one audit")
                .publishPercentileHistogram() // so Grafana can show p95
                .register(registry);
    }

    public <T> T timeAudit(Supplier<T> audit) {
        return auditTimer.record(audit);
    }

    /** kind: bill or clauses. Counted once per job that gave up. */
    public void extractionFailed(String kind) {
        Counter.builder("fairexit.extraction.failures").tag("kind", kind)
                .description("Extractions that gave up after their retries").register(registry).increment();
    }

    /** result: success, cache_hit, quota_exceeded or error. */
    public void llmCall(String result) {
        Counter.builder("fairexit.llm.calls").tag("result", result).register(registry).increment();
    }

    /** Tokens billed by the LLM provider; cost per day = tokens x price, worked out in Grafana. */
    public void llmTokens(long input, long output) {
        Counter.builder("fairexit.llm.tokens").tag("type", "input").register(registry).increment(input);
        Counter.builder("fairexit.llm.tokens").tag("type", "output").register(registry).increment(output);
    }

    public MeterRegistry registry() {
        return registry;
    }
}
