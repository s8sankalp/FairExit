package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.RulesCoverage;
import java.util.List;
import java.util.Map;

/**
 * The same rules as the V4 migration seed, built in code so unit tests need no database.
 * RuleCatalogIntegrationTest checks that the seed and this copy give identical verdicts.
 */
public final class TestRules {

    private TestRules() {
    }

    public static RuleSpec r1() {
        return new RuleSpec("R1", 1, "DUES_MATCH", 10,
                Map.of("tolerance", 1, "maxRentMonths", 3), "Matches {match}.");
    }

    public static RuleSpec r2() {
        return new RuleSpec("R2", 1, "AGREEMENT_CHECK", 20,
                Map.of("requiresClause", List.of("SOCIETY_CHARGE", "OTHER")),
                "Your agreement has no clause that lets the landlord charge for \"{item}\" ({category}).");
    }

    public static RuleSpec r3() {
        return new RuleSpec("R3", 1, "WEAR_AND_TEAR", 30,
                Map.of("categories", List.of("PAINTING", "CLEANING"), "minTenancyMonths", 11),
                "\"{item}\" after {months} months is usually treated as normal wear and tear,"
                        + " and no agreement clause makes the tenant pay for it.");
    }

    /** Version 2: silent when the tenant's move out photos show real damage. */
    public static RuleSpec r3v2() {
        return new RuleSpec("R3", 2, "WEAR_AND_TEAR", 30,
                Map.of("categories", List.of("PAINTING", "CLEANING"), "minTenancyMonths", 11,
                        "skipWhenDamageShown", true),
                "\"{item}\" after {months} months is usually treated as normal wear and tear,"
                        + " and no agreement clause makes the tenant pay for it.");
    }

    public static RuleSpec r4() {
        return new RuleSpec("R4", 1, "PRESENT_AT_MOVE_IN", 40, Map.of(),
                "\"{item}\": your move in photos show this was already there when you moved in.");
    }

    public static RuleSpec r5() {
        return new RuleSpec("R5", 1, "PRICE_CHECK", 50,
                Map.of("categories", List.of("PAINTING", "CLEANING", "REPAIR"), "minPoints", 10),
                "\"{item}\" at {amount} is above the usual range: 9 in 10 of {n} known prices for {category}"
                        + " for a {flatSize} in {city} are {p90} or less.");
    }

    public static RuleSpec r6() {
        return new RuleSpec("R6", 1, "DAMAGE_WITH_EVIDENCE", 60,
                Map.of("categories", List.of("PAINTING", "CLEANING", "REPAIR", "OTHER"), "minPoints", 10),
                "\"{item}\": the damage shows in your move out photos but not in your move in photos,"
                        + " and the price is within the usual range.");
    }

    public static RuleSpec r7() {
        return new RuleSpec("R7", 1, "NEEDS_INFO", 70,
                Map.of("questions", Map.of(
                        "PAINTING", "Ask for photos of the wall damage and the painter's invoice.",
                        "CLEANING", "Ask for the cleaning invoice and what was cleaned beyond normal cleaning.",
                        "REPAIR", "Ask for photos of the damage and the repair invoice.",
                        "UTILITY", "Ask for a copy of the bill this amount comes from.",
                        "RENT", "Ask which months of rent this covers.",
                        "SOCIETY_CHARGE", "Ask which agreement clause or society rule allows this charge.",
                        "OTHER", "Ask what this charge is for and which agreement clause allows it.")),
                "Not enough information to decide. Ask the landlord: {question}");
    }

    /** What the catalog picks today: the highest version of every rule. */
    public static RuleSet defaults() {
        return RuleSet.of(List.of(r1(), r2(), r3v2(), r4(), r5(), r6(), r7()), RulesCoverage.FULL);
    }

    /** The module 3 rule set (rules 1, 2, 3 and 7 only). */
    public static RuleSet module3() {
        return RuleSet.of(List.of(r1(), r2(), r3(), r7()), RulesCoverage.FULL);
    }
}
