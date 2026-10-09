package sg.hawkscc.platform.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Append-only record of sensitive changes (docs/10). Call it inside the same transaction as
 * the change, so a change is never saved without its audit row.
 */
@Component
public class AuditLog {

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param actor   the member who did it, or null for the system
     * @param details small string facts (no secrets), stored as JSON
     */
    public void record(UUID clubId, UUID actor, String action, String targetType, UUID targetId,
                       Map<String, String> details) {
        jdbc.sql("""
                        insert into audit_log (club_id, actor_member_id, action, target_type, target_id, details)
                        values (:club, :actor, :action, :type, :target, cast(:details as jsonb))""")
                .param("club", clubId).param("actor", actor).param("action", action)
                .param("type", targetType).param("target", targetId).param("details", json(details))
                .update();
    }

    static String json(Map<String, String> details) {
        StringBuilder out = new StringBuilder("{");
        details.forEach((k, v) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(quote(k)).append(':').append(v == null ? "null" : quote(v));
        });
        return out.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
