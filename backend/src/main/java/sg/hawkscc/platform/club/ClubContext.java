package sg.hawkscc.platform.club;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The club this deployment serves. Data is tenant-aware (club_id everywhere) but only
 * one club is deployed, chosen by {@code hawks.club-slug}.
 */
@Component
public class ClubContext {

    private final JdbcClient jdbc;
    private final String slug;
    private volatile Club club;

    public record Club(UUID id, String slug, String name) {
    }

    public ClubContext(JdbcClient jdbc, @Value("${hawks.club-slug}") String slug) {
        this.jdbc = jdbc;
        this.slug = slug;
    }

    public UUID clubId() {
        return current().id();
    }

    public Club current() {
        Club c = club;
        if (c == null) {
            c = jdbc.sql("select id, slug, name from clubs where slug = :slug")
                    .param("slug", slug)
                    .query((rs, n) -> new Club(rs.getObject("id", UUID.class), rs.getString("slug"),
                            rs.getString("name")))
                    .optional()
                    .orElseThrow(() -> new IllegalStateException("No club with slug '" + slug + "'"));
            club = c;
        }
        return c;
    }
}
