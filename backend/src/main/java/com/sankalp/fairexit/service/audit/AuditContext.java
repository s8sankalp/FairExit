package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the rules may read about the tenancy. Built once per audit, before the
 * engine runs, so the engine itself never touches the database or Redis.
 *
 * @param agreement  null when no agreement is on file yet. That is different from an
 *                   agreement with no clauses: "unknown" must never be read as "not allowed".
 * @param knownBills utility bills on file, for rule 1
 * @param prices     the usual price range per category for this city and flat size, for rules 5 and 6
 */
public record AuditContext(
        IndianState state,
        FlatSize flatSize,
        String city,
        BigDecimal rent,
        BigDecimal deposit,
        int tenancyMonths,
        AgreementTerms agreement,
        List<KnownBill> knownBills,
        Map<DeductionCategory, PriceStat> prices) {

    public AuditContext {
        knownBills = knownBills == null ? List.of() : List.copyOf(knownBills);
        prices = prices == null ? Map.of() : Map.copyOf(prices);
    }

    /** No price data. */
    public AuditContext(IndianState state, FlatSize flatSize, String city, BigDecimal rent, BigDecimal deposit,
                        int tenancyMonths, AgreementTerms agreement, List<KnownBill> knownBills) {
        this(state, flatSize, city, rent, deposit, tenancyMonths, agreement, knownBills, Map.of());
    }

    public boolean agreementKnown() {
        return agreement != null;
    }

    public Optional<PriceStat> priceFor(DeductionCategory category) {
        return Optional.ofNullable(prices.get(category));
    }

    /** What the rent agreement says the tenant can be charged for. */
    public record AgreementTerms(Set<DeductionCategory> tenantPays) {

        public AgreementTerms {
            tenantPays = Set.copyOf(tenantPays);
        }

        public boolean makesTenantPay(DeductionCategory category) {
            return tenantPays.contains(category);
        }
    }

    public record KnownBill(DeductionCategory category, BigDecimal amount) {
    }

    /** n is the number of real prices behind p50 and p90. */
    public record PriceStat(BigDecimal p50, BigDecimal p90, int n) {
    }
}
