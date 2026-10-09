package sg.hawkscc.platform.stats.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatKind;
import sg.hawkscc.platform.stats.domain.StatLine;
import sg.hawkscc.platform.stats.importing.StandingsCsvParser;
import sg.hawkscc.platform.stats.importing.StatColumn;

/** Plain SQL via JdbcClient: easy to read, and easy to step through in a debugger. */
@Repository
public class StatsRepository {

    private final JdbcClient jdbc;

    public StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- competitions ----------

    public record CompetitionRow(UUID id, Source source, String name, String season, String externalId) {
    }

    public UUID ensureCompetition(UUID clubId, Source source, String name, String season, String externalId) {
        return jdbc.sql("""
                        insert into competitions (club_id, source, name, season, external_id)
                        values (:club, :source, :name, :season, :ext)
                        on conflict (club_id, source, name)
                        do update set season = coalesce(excluded.season, competitions.season),
                                      external_id = coalesce(excluded.external_id, competitions.external_id)
                        returning id""")
                .param("club", clubId).param("source", source.code()).param("name", name)
                .param("season", season).param("ext", externalId)
                .query(UUID.class).single();
    }

    public Optional<CompetitionRow> findCompetition(UUID clubId, UUID id) {
        return jdbc.sql("select * from competitions where club_id = :club and id = :id")
                .param("club", clubId).param("id", id)
                .query(StatsRepository::competition).optional();
    }

    public List<CompetitionRow> competitions(UUID clubId) {
        return jdbc.sql("select * from competitions where club_id = :club order by name")
                .param("club", clubId)
                .query(StatsRepository::competition).list();
    }

    private static CompetitionRow competition(ResultSet rs, int n) throws SQLException {
        return new CompetitionRow(rs.getObject("id", UUID.class), Source.fromCode(rs.getString("source")),
                rs.getString("name"), rs.getString("season"), rs.getString("external_id"));
    }

    // ---------- raw records ----------

    public boolean recordExists(UUID clubId, Source source, UUID competitionId, StatKind kind, String sha256) {
        return jdbc.sql("""
                        select count(*) from source_records
                        where club_id = :club and source = :source and competition_id = :comp
                          and kind = :kind and content_sha256 = :sha""")
                .param("club", clubId).param("source", source.code()).param("comp", competitionId)
                .param("kind", kind.code()).param("sha", sha256)
                .query(Long.class).single() > 0;
    }

    public UUID insertSourceRecord(UUID clubId, Source source, UUID competitionId, StatKind kind, String content,
                                   String sha256, String origin, LocalDate capturedOn, String actor) {
        return jdbc.sql("""
                        insert into source_records (club_id, source, competition_id, kind, raw_content,
                                                    content_sha256, origin, captured_on, imported_by)
                        values (:club, :source, :comp, :kind, :content, :sha, :origin, :captured, :actor)
                        returning id""")
                .param("club", clubId).param("source", source.code()).param("comp", competitionId)
                .param("kind", kind.code()).param("content", content).param("sha", sha256)
                .param("origin", origin).param("captured", capturedOn).param("actor", actor)
                .query(UUID.class).single();
    }

    // ---------- players ----------

    public Optional<UUID> findPlayerByAlias(UUID clubId, Source source, String nameKey) {
        return jdbc.sql("select player_id from player_aliases where club_id = :club and source = :source and name_key = :key")
                .param("club", clubId).param("source", source.code()).param("key", nameKey)
                .query(UUID.class).optional();
    }

    public UUID createPlayerWithAlias(UUID clubId, Source source, String nameKey, String sourceName) {
        UUID playerId = jdbc.sql("insert into players (club_id, display_name) values (:club, :name) returning id")
                .param("club", clubId).param("name", sourceName)
                .query(UUID.class).single();
        jdbc.sql("""
                        insert into player_aliases (club_id, source, name_key, source_name, player_id)
                        values (:club, :source, :key, :name, :player)""")
                .param("club", clubId).param("source", source.code()).param("key", nameKey)
                .param("name", sourceName).param("player", playerId)
                .update();
        return playerId;
    }

    public Optional<UUID> findPlayerByName(UUID clubId, String displayName) {
        return jdbc.sql("""
                        select id from players where club_id = :club and lower(display_name) = lower(:name)
                        order by created_at limit 1""")
                .param("club", clubId).param("name", displayName)
                .query(UUID.class).optional();
    }

    public UUID createPlayer(UUID clubId, String displayName) {
        return jdbc.sql("insert into players (club_id, display_name) values (:club, :name) returning id")
                .param("club", clubId).param("name", displayName)
                .query(UUID.class).single();
    }

    /** Points a source name at a player, creating the alias or repointing an existing one. */
    public void upsertAlias(UUID clubId, Source source, String nameKey, String sourceName, UUID playerId) {
        jdbc.sql("""
                        insert into player_aliases (club_id, source, name_key, source_name, player_id)
                        values (:club, :source, :key, :name, :player)
                        on conflict (club_id, source, name_key)
                        do update set player_id = excluded.player_id, source_name = excluded.source_name""")
                .param("club", clubId).param("source", source.code()).param("key", nameKey)
                .param("name", sourceName).param("player", playerId)
                .update();
    }

    /**
     * Moves one source's stats rows from one player to another, skipping any competition the
     * target already has a row for. Returns how many rows moved.
     */
    public int moveStats(UUID clubId, UUID fromPlayer, UUID toPlayer, Source source) {
        return jdbc.sql("""
                        update player_competition_stats s set player_id = :to, updated_at = now()
                        where s.club_id = :club and s.player_id = :from and s.source = :source
                          and not exists (select 1 from player_competition_stats t
                                          where t.club_id = s.club_id and t.player_id = :to
                                            and t.competition_id = s.competition_id and t.source = s.source)""")
                .param("club", clubId).param("from", fromPlayer).param("to", toPlayer).param("source", source.code())
                .update();
    }

    public long countStats(UUID clubId, UUID playerId, Source source) {
        return jdbc.sql("""
                        select count(*) from player_competition_stats
                        where club_id = :club and player_id = :p and source = :source""")
                .param("club", clubId).param("p", playerId).param("source", source.code())
                .query(Long.class).single();
    }

    /** Deletes a player left with no aliases and no stats (after its names were linked elsewhere). */
    public int deletePlayerIfOrphan(UUID clubId, UUID playerId) {
        return jdbc.sql("""
                        delete from players p where p.club_id = :club and p.id = :p
                          and not exists (select 1 from player_aliases a where a.player_id = p.id)
                          and not exists (select 1 from player_competition_stats s where s.player_id = p.id)""")
                .param("club", clubId).param("p", playerId)
                .update();
    }

    // ---------- per-player stats ----------

    /** Clears the columns a tab owns, so players missing from a new import become unknown. */
    public int clearTab(UUID clubId, UUID competitionId, Source source, StatKind kind) {
        String sets = Arrays.stream(StatColumn.values())
                .filter(c -> c.owner() == kind)
                .map(c -> c.dbName() + " = null")
                .collect(Collectors.joining(", "));
        return jdbc.sql("update player_competition_stats set " + sets + ", " + recordColumn(kind) + " = null,"
                        + " recovered_columns = " + withoutTab(kind, "recovered_columns") + ","
                        + " updated_at = now()"
                        + " where club_id = :club and competition_id = :comp and source = :source")
                .param("club", clubId).param("comp", competitionId).param("source", source.code())
                .update();
    }

    public void upsertTab(UUID clubId, UUID playerId, UUID competitionId, Source source, StatKind kind,
                          Map<StatColumn, Object> values, List<StatColumn> recovered, UUID recordId) {
        List<StatColumn> columns = values.keySet().stream().sorted().toList();
        String recordCol = recordColumn(kind);
        String insertCols = columns.stream().map(StatColumn::dbName).collect(Collectors.joining(", "));
        String insertVals = columns.stream().map(c -> ":" + c.name()).collect(Collectors.joining(", "));
        String updates = columns.stream()
                .map(c -> c == StatColumn.MATCHES
                        // Several tabs print matches; a blank here never wipes another tab's value.
                        ? "matches = coalesce(excluded.matches, player_competition_stats.matches)"
                        : c.dbName() + " = excluded." + c.dbName())
                .collect(Collectors.joining(", "));
        String sql = "insert into player_competition_stats (club_id, player_id, competition_id, source, "
                + recordCol + ", recovered_columns" + (columns.isEmpty() ? "" : ", " + insertCols) + ")"
                + " values (:club, :player, :comp, :source, :record, :recovered"
                + (columns.isEmpty() ? "" : ", " + insertVals) + ")"
                + " on conflict (club_id, player_id, competition_id, source) do update set "
                + recordCol + " = excluded." + recordCol + ", updated_at = now(), recovered_columns = array_cat("
                + withoutTab(kind, "player_competition_stats.recovered_columns") + ", excluded.recovered_columns)"
                + (columns.isEmpty() ? "" : ", " + updates);
        var spec = jdbc.sql(sql)
                .param("club", clubId).param("player", playerId).param("comp", competitionId)
                .param("source", source.code()).param("record", recordId)
                .param("recovered", recovered.stream().map(StatColumn::dbName).toArray(String[]::new));
        for (StatColumn c : columns) {
            spec = spec.param(c.name(), values.get(c));
        }
        spec.update();
    }

    /** {@code recovered} = column names (e.g. "bat_balls") filled by exact recovery, not printed. */
    public record PlayerStatsRow(UUID playerId, String displayName, Source source, UUID competitionId,
                                 StatLine line, List<String> recovered) {
    }

    public List<PlayerStatsRow> playerStats(UUID clubId, Source source, UUID competitionId) {
        return jdbc.sql("""
                        select s.*, p.display_name
                        from player_competition_stats s
                        join players p on p.id = s.player_id
                        where s.club_id = :club
                          and (cast(:source as text) is null or s.source = :source)
                          and (cast(:comp as uuid) is null or s.competition_id = :comp)
                        order by p.display_name""")
                .param("club", clubId)
                .param("source", source == null ? null : source.code())
                .param("comp", competitionId)
                .query((rs, n) -> new PlayerStatsRow(rs.getObject("player_id", UUID.class),
                        rs.getString("display_name"), Source.fromCode(rs.getString("source")),
                        rs.getObject("competition_id", UUID.class), statLine(rs), recovered(rs)))
                .list();
    }

    private static StatLine statLine(ResultSet rs) throws SQLException {
        return new StatLine(
                integer(rs, "matches"),
                new StatLine.Batting(integer(rs, "bat_inns"), integer(rs, "bat_not_outs"), integer(rs, "bat_runs"),
                        integer(rs, "bat_balls"), integer(rs, "bat_high_score"), integer(rs, "bat_fours"),
                        integer(rs, "bat_sixes")),
                new StatLine.Bowling(integer(rs, "bowl_inns"), integer(rs, "bowl_balls"),
                        integer(rs, "bowl_maidens"), integer(rs, "bowl_runs"), integer(rs, "bowl_wickets")),
                new StatLine.Fielding(integer(rs, "field_catches"), integer(rs, "field_stumpings"),
                        integer(rs, "field_run_outs"), integer(rs, "field_dismissals")),
                new StatLine.Reported(rs.getBigDecimal("reported_bat_avg"), rs.getBigDecimal("reported_bat_sr"),
                        rs.getBigDecimal("reported_econ"), rs.getBigDecimal("reported_bowl_avg")));
    }

    private static List<String> recovered(ResultSet rs) throws SQLException {
        Array a = rs.getArray("recovered_columns");
        return a == null ? List.of() : List.of((String[]) a.getArray());
    }

    /** SQL expression: {@code arrayExpr} without the tab's column names (fixed names, not input). */
    private static String withoutTab(StatKind kind, String arrayExpr) {
        String expr = arrayExpr;
        for (StatColumn c : StatColumn.values()) {
            if (c.owner() == kind) {
                expr = "array_remove(" + expr + ", '" + c.dbName() + "')";
            }
        }
        return expr;
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static String recordColumn(StatKind kind) {
        return switch (kind) {
            case BATTING -> "batting_record_id";
            case BOWLING -> "bowling_record_id";
            case FIELDING -> "fielding_record_id";
            case STANDINGS -> throw new IllegalArgumentException("Standings are not player stats");
        };
    }

    // ---------- standings ----------

    public record StandingRow(String group, int position, String team, boolean clubTeam, Integer matches,
                              Integer won, Integer lost, Integer drawn, Integer tied, Integer noResult,
                              Integer points, String netRunRate, String runsFor, String runsAgainst,
                              String lastFive) {
    }

    public void replaceStandings(UUID clubId, UUID competitionId, String group, UUID recordId,
                                 List<StandingsCsvParser.Row> rows, Predicate<String> isClubTeam) {
        jdbc.sql("delete from standings where club_id = :club and competition_id = :comp and group_name = :group")
                .param("club", clubId).param("comp", competitionId).param("group", group)
                .update();
        for (StandingsCsvParser.Row r : rows) {
            jdbc.sql("""
                            insert into standings (club_id, competition_id, source_record_id, group_name, position,
                                team, is_club_team, matches, won, lost, drawn, tied, no_result, points,
                                net_run_rate, runs_for, runs_against, last_five)
                            values (:club, :comp, :record, :group, :pos, :team, :clubTeam, :m, :w, :l, :d, :t,
                                :nr, :pts, :nrr, :for, :against, :last5)""")
                    .param("club", clubId).param("comp", competitionId).param("record", recordId)
                    .param("group", group).param("pos", r.position()).param("team", r.team())
                    .param("clubTeam", isClubTeam.test(r.team()))
                    .param("m", r.matches()).param("w", r.won()).param("l", r.lost()).param("d", r.drawn())
                    .param("t", r.tied()).param("nr", r.noResult()).param("pts", r.points())
                    .param("nrr", r.netRunRate()).param("for", r.runsFor()).param("against", r.runsAgainst())
                    .param("last5", r.lastFive())
                    .update();
        }
    }

    public List<StandingRow> standings(UUID clubId, UUID competitionId) {
        return jdbc.sql("""
                        select * from standings where club_id = :club and competition_id = :comp
                        order by group_name, position""")
                .param("club", clubId).param("comp", competitionId)
                .query((rs, n) -> new StandingRow(rs.getString("group_name"), rs.getInt("position"),
                        rs.getString("team"), rs.getBoolean("is_club_team"), integer(rs, "matches"),
                        integer(rs, "won"), integer(rs, "lost"), integer(rs, "drawn"), integer(rs, "tied"),
                        integer(rs, "no_result"), integer(rs, "points"), rs.getString("net_run_rate"),
                        rs.getString("runs_for"), rs.getString("runs_against"), rs.getString("last_five")))
                .list();
    }

    // ---------- sync runs ----------

    public void insertSyncRun(UUID clubId, Source source, UUID competitionId, StatKind kind, String status,
                              Integer rowsRead, Integer playersCreated, List<String> warnings, String error,
                              String actor, Instant startedAt) {
        jdbc.sql("""
                        insert into sync_runs (club_id, source, competition_id, kind, status, rows_read,
                                               players_created, warnings, error, triggered_by, started_at)
                        values (:club, :source, :comp, :kind, :status, :rows, :created, :warnings, :error,
                                :actor, :started)""")
                .param("club", clubId).param("source", source.code()).param("comp", competitionId)
                .param("kind", kind.code()).param("status", status).param("rows", rowsRead)
                .param("created", playersCreated).param("warnings", warnings.toArray(String[]::new))
                .param("error", error).param("actor", actor).param("started", Timestamp.from(startedAt))
                .update();
    }

    public record SourceStatus(Source source, Instant lastSucceededAt, Instant lastRunAt, String lastRunStatus,
                               String lastError) {
    }

    public List<SourceStatus> sourceStatuses(UUID clubId) {
        return jdbc.sql("""
                        select r.source,
                               (select max(finished_at) from sync_runs s
                                 where s.club_id = r.club_id and s.source = r.source
                                   and s.status in ('succeeded', 'unchanged')) as last_ok,
                               r.finished_at, r.status, r.error
                        from (select distinct on (source) * from sync_runs
                              where club_id = :club order by source, finished_at desc) r""")
                .param("club", clubId)
                .query((rs, n) -> new SourceStatus(Source.fromCode(rs.getString("source")),
                        instant(rs, "last_ok"), instant(rs, "finished_at"), rs.getString("status"),
                        rs.getString("error")))
                .list();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
