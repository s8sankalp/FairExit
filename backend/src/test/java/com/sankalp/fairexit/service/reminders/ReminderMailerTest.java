package com.sankalp.fairexit.service.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderType;
import jakarta.mail.Session;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReminderMailerTest {

    private static Reminder reminder(ReminderType type) {
        Reminder r = new Reminder(UUID.randomUUID(), type, LocalDate.of(2027, 1, 31), Instant.now());
        ReflectionTestUtils.setField(r, "id", UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return r;
    }

    @Test
    void theMessageIdStaysTheSameEveryTimeTheMessageIsSaved() throws Exception {
        ReminderMailer.FixedIdMessage message = new ReminderMailer.FixedIdMessage(
                Session.getInstance(new Properties()), ReminderMailer.messageId(reminder(ReminderType.MOVE_OUT)));
        message.setText("x");

        message.saveChanges();
        String first = message.getMessageID();
        message.saveChanges();

        assertThat(first).isEqualTo("<reminder-11111111-2222-3333-4444-555555555555@fairexit>");
        assertThat(message.getMessageID()).isEqualTo(first);
    }

    @Test
    void subjectsAndBodiesSayWhatToDo() {
        assertThat(ReminderMailer.subject(reminder(ReminderType.NOTICE_PERIOD)))
                .isEqualTo("FairExit: your notice period starts on Sunday 31 January 2027");
        assertThat(ReminderMailer.body(reminder(ReminderType.MOVE_OUT), "Asha", "Bengaluru"))
                .startsWith("Hello Asha,")
                .contains("take move out photos")
                .endsWith("This is information, not legal advice.\n");
        assertThat(ReminderMailer.body(reminder(ReminderType.REFUND_DUE), null, "Kochi"))
                .startsWith("Hello,").contains("send the firm reply");
    }
}
