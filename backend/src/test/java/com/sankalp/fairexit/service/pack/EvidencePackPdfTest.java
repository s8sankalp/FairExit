package com.sankalp.fairexit.service.pack;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.dto.VerifyResponse;
import com.sankalp.fairexit.model.AgreementStatus;
import com.sankalp.fairexit.model.ClauseType;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.DocumentStatus;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.QuoteSource;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.vault.ChainVerifier;
import com.sankalp.fairexit.service.vault.PdfText;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Module 8 "done when", second half: the PDF builds. No Docker needed. */
class EvidencePackPdfTest {

    private static final Instant T = Instant.parse("2026-11-17T04:30:00Z");
    private static final UUID PHOTO = UUID.randomUUID();
    private static final String FILE_HASH = "ab".repeat(32);

    private static PackContent content(VerifyResponse verify) {
        Tenancy tenancy = new Tenancy(UUID.randomUUID(), "Bengaluru", IndianState.KA, FlatSize.TWO_BHK,
                new BigDecimal("30000"), new BigDecimal("150000"), LocalDate.of(2024, 6, 1), LocalDate.of(2026, 6, 1), 30);
        AuditResponse audit = new AuditResponse(UUID.randomUUID(), UUID.randomUUID(), "R1@1,R2@1,R3@2,R4@1,R5@1,R6@1,R7@1",
                RulesCoverage.FULL, 24,
                new AuditResponse.Totals(new BigDecimal("69700"), new BigDecimal("4700"), new BigDecimal("65000"),
                        BigDecimal.ZERO, new BigDecimal("145300")),
                List.of(new AuditResponse.LineVerdict(UUID.randomUUID(), 1, "Full flat repainting",
                                DeductionCategory.PAINTING, new BigDecimal("55000"), Verdict.QUESTIONABLE,
                                List.of("R3@2"), "Wear and tear after 24 months.", List.of()),
                        new AuditResponse.LineVerdict(UUID.randomUUID(), 2, "Deep cleaning", DeductionCategory.CLEANING,
                                new BigDecimal("8000"), Verdict.QUESTIONABLE, List.of("R5@1"),
                                "\"Deep cleaning\" at ₹8,000 is above the usual range.", List.of())),
                "This is information, not legal advice.", T);
        AgreementResponse agreement = new AgreementResponse(UUID.randomUUID(), UUID.randomUUID(),
                AgreementStatus.EXTRACTED, null, 0,
                List.of(new AgreementResponse.ClauseView(ClauseType.CLEANING, 2,
                        "The Licensee shall bear the cost of normal cleaning.", Map.of("tenantPays", true),
                        QuoteSource.TEXT_LAYER)), T);
        DocumentResponse photo = new DocumentResponse(PHOTO, DocumentType.PHOTO, DocumentPhase.MOVE_IN,
                DocumentStatus.STORED, "tap.png", "image/png", 67, "Bathroom", "Tap", null, FILE_HASH, 1,
                "0".repeat(64), "cd".repeat(32), T);
        return new PackContent("Asha Rao", tenancy, audit, agreement, List.of(photo), Map.of(PHOTO, TestFiles.png()),
                verify, T);
    }

    /** Text of the PDF with line breaks folded to spaces, as table cells wrap long sentences. */
    private static String text(byte[] pdf) {
        return String.join(" ", PdfText.pages(pdf)).replaceAll("\\s+", " ");
    }

    @Test
    void thePackBuildsWithEverySection() {
        byte[] pdf = EvidencePackPdf.build(content(
                new VerifyResponse(UUID.randomUUID(), true, 1, "cd".repeat(32), List.of(), T)));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text)
                .contains("FairExit evidence pack")
                .contains("Asha Rao")
                .contains("2BHK in Bengaluru, Karnataka")
                .contains("Rs. 1,50,000")              // deposit
                .contains("Rs. 1,45,300")              // suggested refund
                .contains("Rs. 8,000 is above")        // reason with the rupee sign swapped
                .contains("Verified when this pack was made")
                .contains("normal cleaning")
                .contains(FILE_HASH)
                .contains("How to check this evidence yourself")
                .contains("not legal advice")
                .doesNotContain("₹");
    }

    @Test
    void aBrokenChainIsPrintedAsAWarningNotHidden() {
        VerifyResponse broken = new VerifyResponse(UUID.randomUUID(), false, 1, "cd".repeat(32),
                List.of(new ChainVerifier.Break(PHOTO, 1, ChainVerifier.Problem.FILE_CHANGED, "The file was changed")),
                T);

        String text = text(EvidencePackPdf.build(content(broken)));

        assertThat(text).contains("WARNING").contains("FILE_CHANGED at record 1").doesNotContain("Verified when");
    }
}
