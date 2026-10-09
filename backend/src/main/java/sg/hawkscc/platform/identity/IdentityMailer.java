package sg.hawkscc.platform.identity;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import sg.hawkscc.platform.club.ClubContext;

/** The invite and password-reset emails. Links open the app at hawks.app.base-url. */
@Component
class IdentityMailer {

    private static final Logger log = LoggerFactory.getLogger(IdentityMailer.class);

    private final MailGateway mail;
    private final ClubContext club;
    private final String baseUrl;

    IdentityMailer(MailGateway mail, ClubContext club, @Value("${hawks.app.base-url}") String baseUrl) {
        this.mail = mail;
        this.club = club;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** Sends the invite. Returns the error to show admins if it couldn't be sent. */
    Optional<String> sendInvite(String to, String name, String invitedBy, String token, Duration validFor) {
        String clubName = club.current().name();
        String link = baseUrl + "/accept-invite?token=" + token;
        String days = validFor.toDays() + " days";
        String by = invitedBy == null ? "The club" : invitedBy;
        String text = """
                Hi %s,

                %s has invited you to the %s app, where you'll see what you owe, your stats and club news.

                Set your password to join:
                %s

                This link works once and expires in %s. If it has expired, ask a club admin to send a new one.
                If you weren't expecting this, you can ignore this email.
                """.formatted(name, by, clubName, link, days);
        String html = page("""
                <p>Hi %s,</p>
                <p>%s has invited you to the %s app, where you'll see what you owe, your stats and club news.</p>
                <p><a href="%s" style="background:#0b2545;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600">Set your password</a></p>
                <p>This link works once and expires in %s. If it has expired, ask a club admin to send a new one.</p>
                <p style="color:#666">If you weren't expecting this, you can ignore this email.</p>""".formatted(
                esc(name), esc(by), esc(clubName), esc(link), days));
        return send(new MailGateway.Email(to, "You're invited to the " + clubName + " app", text, html));
    }

    Optional<String> sendPasswordReset(String to, String name, String token, Duration validFor) {
        String clubName = club.current().name();
        String link = baseUrl + "/reset-password?token=" + token;
        String minutes = validFor.toMinutes() + " minutes";
        String text = """
                Hi %s,

                Someone asked to reset your %s app password. To choose a new one, open:
                %s

                This link works once and expires in %s. If you didn't ask, ignore this email; your password stays the same.
                """.formatted(name, clubName, link, minutes);
        String html = page("""
                <p>Hi %s,</p>
                <p>Someone asked to reset your %s app password.</p>
                <p><a href="%s" style="background:#0b2545;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600">Choose a new password</a></p>
                <p>This link works once and expires in %s. If you didn't ask, ignore this email; your password stays the same.</p>""".formatted(
                esc(name), esc(clubName), esc(link), minutes));
        return send(new MailGateway.Email(to, "Reset your " + clubName + " app password", text, html));
    }

    private Optional<String> send(MailGateway.Email email) {
        try {
            mail.send(email);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Email to {} failed: {}", email.to(), e.getMessage());
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Optional.of(reason.length() > 300 ? reason.substring(0, 300) : reason);
        }
    }

    private static String page(String body) {
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;font-size:15px;line-height:1.5;color:#111\">"
                + body + "</div>";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }
}
