package sg.hawkscc.platform.identity;

/** Sends one email. Throws if it couldn't be handed to the mail server. */
public interface MailGateway {

    /** @param html optional; a plain-text email is sent when it is null */
    record Email(String to, String subject, String text, String html) {
    }

    void send(Email email);
}
