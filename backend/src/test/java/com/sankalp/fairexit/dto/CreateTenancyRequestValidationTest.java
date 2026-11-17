package com.sankalp.fairexit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the validation rules alone, without Spring. */
class CreateTenancyRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CreateTenancyRequest valid() {
        return new CreateTenancyRequest("Bengaluru", IndianState.KA, FlatSize.TWO_BHK,
                new BigDecimal("25000.00"), new BigDecimal("100000"),
                LocalDate.of(2025, 6, 1), LocalDate.of(2026, 5, 31), 30);
    }

    private static Set<String> badFields(CreateTenancyRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aCompleteRequestIsValid() {
        assertThat(badFields(valid())).isEmpty();
    }

    @Test
    void endDateAndNoticeDaysAreOptional() {
        CreateTenancyRequest r = new CreateTenancyRequest("Kochi", IndianState.KL, FlatSize.ONE_BHK,
                BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2025, 1, 1), null, null);
        assertThat(badFields(r)).isEmpty();
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        CreateTenancyRequest r = new CreateTenancyRequest("Kochi", IndianState.KL, FlatSize.ONE_BHK,
                BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 1), 30);
        assertThat(badFields(r)).containsExactly("endDateAfterStartDate");
    }

    @Test
    void moneyMustBePositiveWithAtMostTwoDecimals() {
        CreateTenancyRequest r = new CreateTenancyRequest("Kochi", IndianState.KL, FlatSize.ONE_BHK,
                new BigDecimal("-1"), new BigDecimal("10.555"), LocalDate.of(2025, 1, 1), null, null);
        assertThat(badFields(r)).containsExactlyInAnyOrder("rent", "deposit");
    }

    @Test
    void missingFieldsAndOutOfRangeNoticeAreRejected() {
        CreateTenancyRequest r = new CreateTenancyRequest(" ", null, null, null, null, null, null, 400);
        assertThat(badFields(r)).containsExactlyInAnyOrder(
                "city", "state", "flatSize", "rent", "deposit", "startDate", "noticeDays");
    }
}
