package sg.hawkscc.platform.identity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps emails in memory instead of sending them (hawks.mail.transport=outbox). Used by tests,
 * and handy on a machine without Docker. Never use it on a server: links would only be in memory.
 */
public class OutboxMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(OutboxMailGateway.class);
    private static final int KEEP = 50;

    private final Deque<Email> sent = new ArrayDeque<>();

    @Override
    public synchronized void send(Email email) {
        log.info("Email to {} kept in the outbox: {}", email.to(), email.subject());
        log.debug("{}", email.text());
        sent.addLast(email);
        while (sent.size() > KEEP) {
            sent.removeFirst();
        }
    }

    /** Emails sent to this address, oldest first. */
    public synchronized List<Email> sentTo(String address) {
        return sent.stream().filter(e -> e.to().equalsIgnoreCase(address)).toList();
    }
}
