package com.sankalp.fairexit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sankalp.fairexit.dto.CreateTenancyRequest;
import com.sankalp.fairexit.dto.TenancyResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.TenancyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TenancyServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private TenancyRepository tenancies;

    @Mock
    private RuleCatalog ruleCatalog;

    @InjectMocks
    private TenancyService service;

    private static CreateTenancyRequest request(String city, IndianState state) {
        return new CreateTenancyRequest(city, state, FlatSize.TWO_BHK,
                new BigDecimal("25000"), new BigDecimal("100000"),
                LocalDate.of(2025, 6, 1), null, 30);
    }

    private static Tenancy withId(Tenancy t) {
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    @Test
    void createSavesTheTenancyForTheCallerWithACleanCity() {
        when(tenancies.save(any(Tenancy.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(ruleCatalog.coverageFor(IndianState.KA)).thenReturn(RulesCoverage.FULL);

        TenancyResponse response = service.create(USER, request("  bengaluru ", IndianState.KA));

        ArgumentCaptor<Tenancy> saved = ArgumentCaptor.forClass(Tenancy.class);
        verify(tenancies).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(response.city()).isEqualTo("Bengaluru");
        assertThat(response.stateName()).isEqualTo("Karnataka");
        assertThat(response.rulesCoverage()).isEqualTo(RulesCoverage.FULL);
    }

    @Test
    void coverageComesFromTheRuleCatalog() {
        when(tenancies.save(any(Tenancy.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(ruleCatalog.coverageFor(IndianState.MH)).thenReturn(RulesCoverage.GENERAL_GUIDANCE);

        TenancyResponse response = service.create(USER, request("Pune", IndianState.MH));

        assertThat(response.rulesCoverage()).isEqualTo(RulesCoverage.GENERAL_GUIDANCE);
    }

    @Test
    void someoneElsesTenancyIsNotFound() {
        UUID id = UUID.randomUUID();
        when(tenancies.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, id))
                .isInstanceOf(ApiException.class)
                .hasMessage("Tenancy not found")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listOnlyAsksForTheCallersTenancies() {
        Tenancy t = withId(new Tenancy(USER, "Kochi", IndianState.KL, FlatSize.ONE_BHK,
                new BigDecimal("15000"), new BigDecimal("50000"), LocalDate.of(2024, 1, 1), null, null));
        when(tenancies.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(t));

        assertThat(service.list(USER)).extracting(TenancyResponse::city).containsExactly("Kochi");
    }

    @ParameterizedTest
    @CsvSource({
            "bengaluru, Bengaluru",
            "'  navi   MUMBAI ', Navi Mumbai",
            "KOCHI, Kochi"
    })
    void cityNamesAreNormalised(String input, String expected) {
        assertThat(TenancyService.normaliseCity(input)).isEqualTo(expected);
    }
}
