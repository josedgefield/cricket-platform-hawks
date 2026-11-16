package sg.hawkscc.platform.comms;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the notices API returns. */
public final class NoticeViews {

    private NoticeViews() {
    }

    /**
     * @param reactions  how many members chose each reaction (like, done, cricket, clap, question)
     * @param myReaction the caller's own reaction, if any
     * @param unread     the caller hasn't opened it yet
     * @param readCount  how many members have opened it; admins only, null for players
     * @param email      how the email copy went (sending, sent, failed); admins only, null if none was asked for
     */
    public record Notice(UUID id, String title, String body, boolean pinned, String authorName, Instant createdAt,
                         Instant editedAt, Map<String, Integer> reactions, String myReaction, int replyCount,
                         boolean unread, Integer readCount, EmailCopy email) {
    }

    public record EmailCopy(String status, Integer sent, Integer failed) {
    }

    /** @param body null when the reply is hidden */
    public record Reply(UUID id, String authorName, boolean mine, String body, Instant createdAt, boolean hidden,
                        String hiddenReason) {
    }

    public record NoticeWithReplies(Notice notice, List<Reply> replies) {
    }
}
