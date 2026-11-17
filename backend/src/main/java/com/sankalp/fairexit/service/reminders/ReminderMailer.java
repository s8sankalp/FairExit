package com.sankalp.fairexit.service.reminders;

import com.sankalp.fairexit.config.MailProperties;
import com.sankalp.fairexit.model.Reminder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Builds and sends one reminder email.
 *
 * The Message-ID is fixed per reminder (not random). If the app crashes after the mail
 * server accepted the email but before the database recorded SENT, the retry sends the
 * same Message-ID, and mail clients and servers treat it as the same email.
 */
@Component
public class ReminderMailer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public ReminderMailer(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public static String messageId(Reminder reminder) {
        return "<reminder-" + reminder.getId() + "@fairexit>";
    }

    public void send(Reminder reminder, String toEmail, String toName, String city) throws MessagingException {
        MimeMessage message = new FixedIdMessage(Session.getInstance(new Properties()), messageId(reminder));
        message.setFrom(new InternetAddress(properties.from()));
        message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject(reminder), StandardCharsets.UTF_8.name());
        message.setText(body(reminder, toName, city), StandardCharsets.UTF_8.name());
        mailSender.send(message);
    }

    static String subject(Reminder r) {
        String date = DATE.format(r.getEventDate());
        return switch (r.getType()) {
            case NOTICE_PERIOD -> "FairExit: your notice period starts on " + date;
            case MOVE_OUT -> "FairExit: you move out on " + date;
            case REFUND_DUE -> "FairExit: your deposit refund is due on " + date;
        };
    }

    static String body(Reminder r, String name, String city) {
        String date = DATE.format(r.getEventDate());
        String hello = "Hello" + (name == null || name.isBlank() ? "" : " " + name.trim()) + ",\n\n";
        String what = switch (r.getType()) {
            case NOTICE_PERIOD -> "To leave your flat in " + city + " on time, your notice period has to start on "
                    + date + ". Give your landlord written notice by then, and keep a copy.";
            case MOVE_OUT -> "You move out of your flat in " + city + " on " + date + ". Before you hand over the "
                    + "keys, take move out photos of every room and upload them to FairExit. They are your proof.";
            case REFUND_DUE -> "Your deposit refund for the flat in " + city + " is due on " + date + ". If you have "
                    + "not received it, open your audit in FairExit and send the firm reply.";
        };
        return hello + what + "\n\nFairExit\n\nThis is information, not legal advice.\n";
    }

    /** JavaMail normally makes a new random Message-ID on every send; this keeps ours. */
    static final class FixedIdMessage extends MimeMessage {

        private final String id;

        FixedIdMessage(Session session, String id) {
            super(session);
            this.id = id;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", id);
        }
    }
}
