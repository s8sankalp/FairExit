package com.sankalp.fairexit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.BillStatus;
import com.sankalp.fairexit.model.DeductionBill;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.AuditRepository;
import com.sankalp.fairexit.service.audit.AuditEngine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final UUID USER = UUID.randomUUID();
    // "Today" is fixed, so tenancy length for an open tenancy is predictable
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BillService billService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private RuleCatalog ruleCatalog;
    @Mock
    private AuditRepository audits;
    @Mock
    private AgreementService agreements;
    @Mock
    private com.sankalp.fairexit.service.vault.VaultService vault;
    @Mock
    private EvidenceService evidence;
    @Mock
    private PriceService prices;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(billService, tenancyService, ruleCatalog, new AuditEngine(), audits, agreements,
                vault, evidence, prices,
                new com.sankalp.fairexit.config.FairExitMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), CLOCK);
    }

    private static Tenancy tenancy(String start, String end) {
        return new Tenancy(USER, "Bengaluru", IndianState.KA, FlatSize.TWO_BHK,
                new BigDecimal("30000"), new BigDecimal("150000"),
                LocalDate.parse(start), end == null ? null : LocalDate.parse(end), 30);
    }

    @ParameterizedTest
    @CsvSource({
            "2024-06-01, 2026-06-01, 24",
            "2025-06-01, 2026-05-31, 11",   // one day short of 12 months still counts as 11
            "2025-06-01, 2026-04-30, 10",
            "2026-11-01, 2026-11-10, 0"
    })
    void countsWholeMonthsToTheEndDate(String start, String end, int expected) {
        assertThat(service.tenancyMonths(tenancy(start, end))).isEqualTo(expected);
    }

    @Test
    void anOpenTenancyIsCountedUpToToday() {
        assertThat(service.tenancyMonths(tenancy("2025-11-17", null))).isEqualTo(12);
    }

    @Test
    void aBillStillBeingExtractedCannotBeAudited() {
        UUID billId = UUID.randomUUID();
        DeductionBill bill = DeductionBill.manual(UUID.randomUUID());
        ReflectionTestUtils.setField(bill, "status", BillStatus.EXTRACTING);
        when(billService.findOwned(USER, billId)).thenReturn(bill);

        assertThatThrownBy(() -> service.run(USER, billId))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(audits);
    }

    @Test
    void someoneElsesAuditIsNotFound() {
        UUID auditId = UUID.randomUUID();
        when(audits.findOwned(auditId, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, auditId))
                .hasMessage("Audit not found")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
