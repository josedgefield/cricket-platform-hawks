package sg.hawkscc.platform.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.club.ClubContext;
import sg.hawkscc.platform.stats.domain.NameKey;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.importing.PlayerLinksCsv;
import sg.hawkscc.platform.stats.persistence.StatsRepository;

/**
 * Says which source names are the same person ("Shreyas Puttur" on SCA = "Puttur Shreyas" on
 * CricHeroes), so their figures add up as one player. Linking a name that already has stats
 * under another player moves those stats across; it refuses (and changes nothing) if both
 * players have figures for the same competition, since adding them would double count.
 */
@Service
public class PlayerLinkService {

    private static final Logger log = LoggerFactory.getLogger(PlayerLinkService.class);

    private final StatsRepository repo;
    private final ClubContext club;
    private final TransactionTemplate tx;

    public PlayerLinkService(StatsRepository repo, ClubContext club, TransactionTemplate tx) {
        this.repo = repo;
        this.club = club;
        this.tx = tx;
    }

    public enum Outcome { LINKED, UNCHANGED }

    public record LinkResult(UUID playerId, String player, String source, String sourceName, Outcome outcome,
                             int rowsMoved) {
    }

    public LinkResult link(String player, Source source, String sourceName, String actor) {
        String name = player == null ? "" : player.strip();
        String printed = sourceName == null ? "" : sourceName.strip();
        if (name.isEmpty() || printed.isEmpty()) {
            throw new IllegalArgumentException("Both the player and the source name are required");
        }
        UUID clubId = club.clubId();
        String key = NameKey.of(printed);
        LinkResult result = tx.execute(status -> {
            UUID target = repo.findPlayerByName(clubId, name).orElseGet(() -> repo.createPlayer(clubId, name));
            var current = repo.findPlayerByAlias(clubId, source, key);
            if (current.isPresent() && current.get().equals(target)) {
                return new LinkResult(target, name, source.code(), printed, Outcome.UNCHANGED, 0);
            }
            int moved = 0;
            if (current.isPresent()) {
                UUID previous = current.get();
                moved = repo.moveStats(clubId, previous, target, source);
                if (repo.countStats(clubId, previous, source) > 0) {
                    // Thrown inside the transaction, so the moves above roll back.
                    throw new ConflictException("\"" + printed + "\" (" + source.code() + ") and \"" + name
                            + "\" both have " + source.code() + " figures for the same competition; "
                            + "linking them would count those figures twice. Nothing was changed.");
                }
            }
            repo.upsertAlias(clubId, source, key, printed, target);
            current.ifPresent(previous -> repo.deletePlayerIfOrphan(clubId, previous));
            return new LinkResult(target, name, source.code(), printed, Outcome.LINKED, moved);
        });
        if (result.outcome() == Outcome.LINKED) {
            log.info("player link: {} ({}) -> {} by {}, {} stats rows moved", printed, source.code(), name, actor,
                    result.rowsMoved());
        }
        return result;
    }

    /** Applies a whole link list (see {@link PlayerLinksCsv}); stops at the first conflict. */
    public List<LinkResult> linkAll(String csv, String actor) {
        var parsed = PlayerLinksCsv.parse(csv);
        if (!parsed.errors().isEmpty()) {
            throw new ImportRejectedException(parsed.errors());
        }
        List<LinkResult> results = new ArrayList<>();
        for (var l : parsed.links()) {
            results.add(link(l.player(), l.source(), l.sourceName(), actor));
        }
        return results;
    }
}
