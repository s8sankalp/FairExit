package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.service.audit.AuditContext.AgreementTerms;
import com.sankalp.fairexit.service.audit.AuditContext.KnownBill;
import com.sankalp.fairexit.service.audit.AuditContext.PriceStat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The worked example from the FairExit research report, section 5:
 * a 2BHK in Bengaluru, 2 years of tenancy, deposit ₹1,50,000, five deduction lines.
 *
 * What is on file:
 *  - the agreement mentions "normal cleaning" and nothing about painting, damage or society charges
 *  - the last electricity bill, ₹3,200
 *  - for the tap: a move in photo showing it fine and a move out photo showing it broken
 *  - usual prices for a 2BHK in Bengaluru: cleaning up to ₹6,000 (12 quotes), tap repair
 *    up to ₹2,000 (15 quotes); no painting prices yet
 */
public final class WorkedExample {

    public static final UUID TAP_MOVE_IN_PHOTO = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID TAP_MOVE_OUT_PHOTO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    public static final LineInput PAINTING = line(1, "Full flat repainting", DeductionCategory.PAINTING, "55000", null,
            List.of());
    public static final LineInput LIFT = line(2, "Lift usage", DeductionCategory.SOCIETY_CHARGE, "2000", null,
            List.of());
    public static final LineInput CLEANING = line(3, "Deep cleaning", DeductionCategory.CLEANING, "8000", null,
            List.of());
    public static final LineInput TAP = line(4, "Broken bathroom tap", DeductionCategory.REPAIR, "1500", "Bathroom",
            List.of(new LineInput.Evidence(TAP_MOVE_IN_PHOTO, DocumentPhase.MOVE_IN, EvidenceObservation.NO_DAMAGE),
                    new LineInput.Evidence(TAP_MOVE_OUT_PHOTO, DocumentPhase.MOVE_OUT,
                            EvidenceObservation.DAMAGE_PRESENT)));
    public static final LineInput ELECTRICITY = line(5, "Pending electricity bill", DeductionCategory.UTILITY, "3200",
            null, List.of());

    private WorkedExample() {
    }

    public static List<LineInput> lines() {
        return List.of(PAINTING, LIFT, CLEANING, TAP, ELECTRICITY);
    }

    public static AuditContext context() {
        return new AuditContext(IndianState.KA, FlatSize.TWO_BHK, "Bengaluru",
                new BigDecimal("30000"), new BigDecimal("150000"), 24,
                new AgreementTerms(Set.of(DeductionCategory.CLEANING)),
                List.of(new KnownBill(DeductionCategory.UTILITY, new BigDecimal("3200"))),
                Map.of(DeductionCategory.CLEANING, new PriceStat(new BigDecimal("4500"), new BigDecimal("6000"), 12),
                        DeductionCategory.REPAIR, new PriceStat(new BigDecimal("1200"), new BigDecimal("2000"), 15)));
    }

    private static LineInput line(int n, String item, DeductionCategory category, String amount, String room,
                                  List<LineInput.Evidence> evidence) {
        UUID id = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
        return new LineInput(id, item, category, new BigDecimal(amount), room, evidence);
    }
}
