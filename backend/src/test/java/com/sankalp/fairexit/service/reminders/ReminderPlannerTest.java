package com.sankalp.fairexit.service.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.ReminderType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderPlannerTest {

    // 17 Nov 2026, 08:00 IST
    private static final Instant NOW = Instant.parse("2026-11-17T02:30:00Z");

    @Test
    void plansAllThreeAt9amIndiaTime() {
        List<ReminderPlanner.Planned> plan = ReminderPlanner.plan(LocalDate.of(2027, 1, 31), 30, 30, 9, NOW);

        assertThat(plan).extracting(ReminderPlanner.Planned::type)
                .containsExactly(ReminderType.NOTICE_PERIOD, ReminderType.MOVE_OUT, ReminderType.REFUND_DUE);
        // Notice starts 1 Jan; reminder 3 days before, 09:00 IST = 03:30 UTC
        assertThat(plan.get(0).eventDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(plan.get(0).dueAt()).isEqualTo(Instant.parse("2026-12-29T03:30:00Z"));
        // Move out 31 Jan; reminder the day before
        assertThat(plan.get(1).dueAt()).isEqualTo(Instant.parse("2027-01-30T03:30:00Z"));
        // Refund due 30 days after move out, reminder on the day
        assertThat(plan.get(2).eventDate()).isEqualTo(LocalDate.of(2027, 3, 2));
        assertThat(plan.get(2).dueAt()).isEqualTo(Instant.parse("2027-03-02T03:30:00Z"));
    }

    @Test
    void theAgreementsRefundDaysAreUsed() {
        List<ReminderPlanner.Planned> plan = ReminderPlanner.plan(LocalDate.of(2027, 1, 31), null, 15, 9, NOW);

        assertThat(plan).extracting(ReminderPlanner.Planned::type)
                .containsExactly(ReminderType.MOVE_OUT, ReminderType.REFUND_DUE); // no notice days: no notice reminder
        assertThat(plan.get(1).eventDate()).isEqualTo(LocalDate.of(2027, 2, 15));
    }

    @Test
    void aSendTimeAlreadyPastIsSentNowAndAPastEventIsSkipped() {
        // Move out tomorrow, 18 Nov: the notice period started 19 Oct (past), the move out
        // reminder time was today 09:00 IST, still ahead at 08:00, the refund is in the future
        List<ReminderPlanner.Planned> plan = ReminderPlanner.plan(LocalDate.of(2026, 11, 18), 30, 30, 9, NOW);

        assertThat(plan).extracting(ReminderPlanner.Planned::type)
                .containsExactly(ReminderType.MOVE_OUT, ReminderType.REFUND_DUE);
        assertThat(plan.get(0).dueAt()).isEqualTo(Instant.parse("2026-11-17T03:30:00Z"));

        Instant afterNine = Instant.parse("2026-11-17T06:00:00Z");
        assertThat(ReminderPlanner.plan(LocalDate.of(2026, 11, 18), 30, 30, 9, afterNine).get(0).dueAt())
                .isEqualTo(afterNine); // too late for 09:00 today, so straight away
    }
}
