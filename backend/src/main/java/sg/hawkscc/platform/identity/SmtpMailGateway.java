package sg.hawkscc.platform.identity;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends through the SMTP server in spring.mail.* (Mailpit locally, e.g. Brevo in production). */
class SmtpMailGateway implements MailGateway {

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    SmtpMailGateway(ObjectProvider<JavaMailSender> sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(Email email) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            throw new IllegalStateException("Email isn't set up on the server (spring.mail.host).");
        }
        try {
            MimeMessage message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mail.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Couldn't build the email: " + e.getMessage(), e);
        }
    }
}
