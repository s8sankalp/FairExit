package com.sankalp.fairexit.service;

import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.RuleDefinition;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.repository.RuleRepository;
import com.sankalp.fairexit.service.audit.RuleSet;
import com.sankalp.fairexit.service.audit.RuleSpec;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Picks the rules for a state. For each rule id: the state's own row if there is one,
 * otherwise the general row (state NULL); the highest enabled version wins.
 *
 * Rules only change through Flyway migrations, which need a restart, so caching the
 * built rule sets in memory for the life of the app is safe.
 */
@Service
public class RuleCatalog {

    private final RuleRepository rules;
    private final Map<IndianState, RuleSet> cache = new ConcurrentHashMap<>();

    public RuleCatalog(RuleRepository rules) {
        this.rules = rules;
    }

    public RuleSet ruleSetFor(IndianState state) {
        return cache.computeIfAbsent(state, this::build);
    }

    /** FULL only when the state has rows of its own in the rules table. */
    public RulesCoverage coverageFor(IndianState state) {
        return ruleSetFor(state).coverage();
    }

    private RuleSet build(IndianState state) {
        List<RuleDefinition> enabled = rules.findByEnabledTrue();
        boolean stateHasOwnRules = enabled.stream().anyMatch(r -> r.getState() == state);

        Map<String, List<RuleDefinition>> byRuleId = enabled.stream()
                .filter(r -> r.getState() == null || r.getState() == state)
                .collect(Collectors.groupingBy(RuleDefinition::getRuleId));

        List<RuleSpec> chosen = byRuleId.values().stream()
                .map(candidates -> pick(candidates, state))
                .flatMap(Optional::stream)
                .map(RuleCatalog::toSpec)
                .toList();

        return RuleSet.of(chosen, stateHasOwnRules ? RulesCoverage.FULL : RulesCoverage.GENERAL_GUIDANCE);
    }

    private static Optional<RuleDefinition> pick(List<RuleDefinition> candidates, IndianState state) {
        Comparator<RuleDefinition> byVersion = Comparator.comparingInt(RuleDefinition::getVersion);
        Optional<RuleDefinition> own = candidates.stream()
                .filter(r -> r.getState() == state)
                .max(byVersion);
        return own.isPresent()
                ? own
                : candidates.stream().filter(r -> Objects.isNull(r.getState())).max(byVersion);
    }

    private static RuleSpec toSpec(RuleDefinition r) {
        return new RuleSpec(r.getRuleId(), r.getVersion(), r.getRuleType(), r.getPriority(),
                r.getParameters(), r.getReasonTemplate());
    }
}
