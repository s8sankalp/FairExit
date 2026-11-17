package com.sankalp.fairexit.service.reminders;

import com.sankalp.fairexit.model.ReminderType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out which reminders a tenancy needs and when to send them. Pure: dates in, plan out.
 *
 *  NOTICE_PERIOD  event = move out minus notice days;  sent 3 days before, at send hour
 *  MOVE_OUT       event = move out day;                 sent 1 day before
 *  REFUND_DUE     event = move out plus refund days;    sent on the day
 *
 * A reminder whose send time is already past but whose event is today or later is sent
 * straight away; a reminder about an event already past is skipped.
 */
public final class ReminderPlanner {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final int NOTICE_DAYS_AHEAD = 3;

    public record Planned(ReminderType type, LocalDate eventDate, Instant dueAt) {
    }

    private ReminderPlanner() {
    }

    /**
     * @param noticeDays may be null (unknown): then no notice reminder
     */
    public static List<Planned> plan(LocalDate moveOut, Integer noticeDays, int refundDays, int sendHour,
                                     Instant now) {
        List<Planned> plan = new ArrayList<>();
        if (noticeDays != null && noticeDays > 0) {
            LocalDate noticeStart = moveOut.minusDays(noticeDays);
            add(plan, ReminderType.NOTICE_PERIOD, noticeStart, noticeStart.minusDays(NOTICE_DAYS_AHEAD), sendHour, now);
        }
        add(plan, ReminderType.MOVE_OUT, moveOut, moveOut.minusDays(1), sendHour, now);
        LocalDate refundDue = moveOut.plusDays(refundDays);
        add(plan, ReminderType.REFUND_DUE, refundDue, refundDue, sendHour, now);
        return plan;
    }

    private static void add(List<Planned> plan, ReminderType type, LocalDate event, LocalDate sendDay, int sendHour,
                            Instant now) {
        LocalDate today = LocalDate.ofInstant(now, INDIA);
        if (event.isBefore(today)) {
            return;
        }
        Instant due = sendDay.atTime(LocalTime.of(sendHour, 0)).atZone(INDIA).toInstant();
        plan.add(new Planned(type, event, due.isBefore(now) ? now : due));
    }
}
