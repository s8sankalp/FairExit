package com.sankalp.fairexit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.service.audit.AuditEngine;
import com.sankalp.fairexit.service.audit.AuditOutcome;
import com.sankalp.fairexit.service.audit.RuleSet;
import com.sankalp.fairexit.service.audit.TestRules;
import com.sankalp.fairexit.service.audit.WorkedExample;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Checks the rules really seeded by Flyway, not the copy used in unit tests. */
class RuleCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RuleCatalog catalog;

    @Test
    void karnatakaAndKeralaHaveTheirOwnRules() {
        assertThat(catalog.coverageFor(IndianState.KA)).isEqualTo(RulesCoverage.FULL);
        assertThat(catalog.coverageFor(IndianState.KL)).isEqualTo(RulesCoverage.FULL);
    }

    @Test
    void otherStatesFallBackToGeneralGuidance() {
        RuleSet maharashtra = catalog.ruleSetFor(IndianState.MH);

        assertThat(maharashtra.coverage()).isEqualTo(RulesCoverage.GENERAL_GUIDANCE);
        // R3 has versions 1 and 2; the catalog picks the newest
        assertThat(maharashtra.version()).isEqualTo("R1@1,R2@1,R3@2,R4@1,R5@1,R6@1,R7@1");
    }

    /** Guards against the seed and the unit test copy drifting apart. */
    @Test
    void seededRulesGiveTheSameWorkedExampleResultAsTheUnitTestRules() {
        AuditEngine engine = new AuditEngine();

        AuditOutcome fromDatabase = engine.run(
                WorkedExample.lines(), WorkedExample.context(), catalog.ruleSetFor(IndianState.KA));
        AuditOutcome fromCode = engine.run(
                WorkedExample.lines(), WorkedExample.context(), TestRules.defaults());

        assertThat(fromDatabase).isEqualTo(fromCode);
    }
}
