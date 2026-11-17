package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Tenancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenancyResponse(
        UUID id,
        String city,
        IndianState state,
        String stateName,
        RulesCoverage rulesCoverage,
        FlatSize flatSize,
        BigDecimal rent,
        BigDecimal deposit,
        LocalDate startDate,
        LocalDate endDate,
        Integer noticeDays,
        Instant createdAt) {

    public static TenancyResponse from(Tenancy t, RulesCoverage coverage) {
        return new TenancyResponse(
                t.getId(), t.getCity(), t.getState(), t.getState().displayName(),
                coverage, t.getFlatSize(), t.getRent(), t.getDeposit(),
                t.getStartDate(), t.getEndDate(), t.getNoticeDays(), t.getCreatedAt());
    }
}
