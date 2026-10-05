package sg.hawkscc.platform.stats;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.club.ClubContext;
import sg.hawkscc.platform.stats.domain.NameKey;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatKind;
import sg.hawkscc.platform.stats.importing.LeaderboardCsvParser;
import sg.hawkscc.platform.stats.importing.StandingsCsvParser;
import sg.hawkscc.platform.stats.persistence.StatsRepository;

/**
 * Imports source data (docs/06): keep the raw content, skip identical re-imports,
 * validate before writing, write everything in one transaction, and record every
 * attempt — including failures — as a sync run.
 */
@Service
public class StatsImportService {

    private static final Logger log = LoggerFactory.getLogger(StatsImportService.class);
    private static final int MAX_CONTENT_CHARS = 1_000_000;

    private final StatsRepository repo;
    private final ClubContext club;
    private final TransactionTemplate tx;
    private final Set<String> clubTeamKeys;

    public StatsImportService(StatsRepository repo, ClubContext club, TransactionTemplate tx,
                              @Value("${hawks.stats.club-team-names:Hawks CC}") List<String> clubTeamNames) {
        this.repo = repo;
        this.club = club;
        this.tx = tx;
        this.clubTeamKeys = clubTeamNames.stream().map(NameKey::of).collect(Collectors.toSet());
    }

    public record LeaderboardImport(UUID competitionId, StatKind kind, String content, String origin,
                                    LocalDate capturedOn, String actor) {
    }

    public record StandingsImport(UUID competitionId, String group, String content, String origin,
                                  LocalDate capturedOn, String actor) {
    }

    public UUID ensureCompetition(Source source, String name, String season, String externalId) {
        return repo.ensureCompetition(club.clubId(), source, name, season, externalId);
    }

    public ImportResult importLeaderboard(LeaderboardImport cmd) {
        if (cmd.kind() == StatKind.STANDINGS) {
            throw new IllegalArgumentException("Use importStandings for a points table");
        }
        Instant started = Instant.now();
        UUID clubId = club.clubId();
        var competition = repo.findCompetition(clubId, cmd.competitionId())
                .orElseThrow(() -> new NotFoundException("Competition " + cmd.competitionId() + " not found"));
        Source source = competition.source();
        String content = normalise(cmd.content());
        String sha = sha256(content);

        if (repo.recordExists(clubId, source, competition.id(), cmd.kind(), sha)) {
            repo.insertSyncRun(clubId, source, competition.id(), cmd.kind(), "unchanged", 0, 0, List.of(), null,
                    cmd.actor(), started);
            log.info("stats import unchanged: source={} competition={} kind={}", source.code(), competition.name(),
                    cmd.kind().code());
            return new ImportResult(ImportResult.Status.UNCHANGED, cmd.kind().code(), 0, 0, List.of(), List.of(),
                    null);
        }

        var parsed = LeaderboardCsvParser.parse(content, cmd.kind());
        if (parsed.hasErrors()) {
            fail(clubId, source, competition.id(), cmd.kind(), cmd.actor(), started, parsed.errors());
        }

        try {
            ImportResult result = tx.execute(status -> {
                UUID recordId = repo.insertSourceRecord(clubId, source, competition.id(), cmd.kind(), content, sha,
                        cmd.origin(), cmd.capturedOn(), cmd.actor());
                repo.clearTab(clubId, competition.id(), source, cmd.kind());
                List<String> newPlayers = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                int applied = 0;
                for (var row : parsed.rows()) {
                    row.warnings().forEach(w -> warnings.add("Line " + row.line() + " (" + row.sourceName() + "): " + w));
                    if (row.duplicate()) {
                        continue;
                    }
                    String key = NameKey.of(row.sourceName());
                    UUID playerId = repo.findPlayerByAlias(clubId, source, key).orElseGet(() -> {
                        newPlayers.add(row.sourceName());
                        return repo.createPlayerWithAlias(clubId, source, key, row.sourceName());
                    });
                    repo.upsertTab(clubId, playerId, competition.id(), source, cmd.kind(), row.values(),
                            row.recovered(), recordId);
                    applied++;
                }
                repo.insertSyncRun(clubId, source, competition.id(), cmd.kind(), "succeeded", parsed.rows().size(),
                        newPlayers.size(), warnings, null, cmd.actor(), started);
                return new ImportResult(ImportResult.Status.IMPORTED, cmd.kind().code(), applied, newPlayers.size(),
                        List.copyOf(newPlayers), List.copyOf(warnings), recordId);
            });
            log.info("stats import succeeded: source={} competition={} kind={} rows={} newPlayers={} warnings={}",
                    source.code(), competition.name(), cmd.kind().code(), result.rowsApplied(),
                    result.playersCreated(), result.warnings().size());
            return result;
        } catch (RuntimeException e) {
            recordFailure(clubId, source, competition.id(), cmd.kind(), cmd.actor(), started, e);
            throw e;
        }
    }

    public ImportResult importStandings(StandingsImport cmd) {
        Instant started = Instant.now();
        UUID clubId = club.clubId();
        var competition = repo.findCompetition(clubId, cmd.competitionId())
                .orElseThrow(() -> new NotFoundException("Competition " + cmd.competitionId() + " not found"));
        Source source = competition.source();
        if (cmd.group() == null || cmd.group().isBlank()) {
            throw new IllegalArgumentException("A group name is required (use the competition name if it has none)");
        }
        String content = normalise(cmd.content());
        // The group is part of what was imported, so it is part of the identity of the record.
        String sha = sha256(cmd.group().strip() + "\n" + content);

        if (repo.recordExists(clubId, source, competition.id(), StatKind.STANDINGS, sha)) {
            repo.insertSyncRun(clubId, source, competition.id(), StatKind.STANDINGS, "unchanged", 0, 0, List.of(),
                    null, cmd.actor(), started);
            return new ImportResult(ImportResult.Status.UNCHANGED, StatKind.STANDINGS.code(), 0, 0, List.of(),
                    List.of(), null);
        }
        var parsed = StandingsCsvParser.parse(content);
        if (parsed.hasErrors()) {
            fail(clubId, source, competition.id(), StatKind.STANDINGS, cmd.actor(), started, parsed.errors());
        }
        try {
            return tx.execute(status -> {
                UUID recordId = repo.insertSourceRecord(clubId, source, competition.id(), StatKind.STANDINGS,
                        content, sha, cmd.origin(), cmd.capturedOn(), cmd.actor());
                repo.replaceStandings(clubId, competition.id(), cmd.group().strip(), recordId, parsed.rows(),
                        team -> clubTeamKeys.contains(NameKey.of(team)));
                repo.insertSyncRun(clubId, source, competition.id(), StatKind.STANDINGS, "succeeded",
                        parsed.rows().size(), 0, List.of(), null, cmd.actor(), started);
                return new ImportResult(ImportResult.Status.IMPORTED, StatKind.STANDINGS.code(),
                        parsed.rows().size(), 0, List.of(), List.of(), recordId);
            });
        } catch (RuntimeException e) {
            recordFailure(clubId, source, competition.id(), StatKind.STANDINGS, cmd.actor(), started, e);
            throw e;
        }
    }

    private void fail(UUID clubId, Source source, UUID competitionId, StatKind kind, String actor, Instant started,
                      List<String> errors) {
        repo.insertSyncRun(clubId, source, competitionId, kind, "failed", null, null, List.of(),
                String.join("; ", errors), actor, started);
        log.warn("stats import rejected: source={} kind={} errors={}", source.code(), kind.code(), errors);
        throw new ImportRejectedException(errors);
    }

    private void recordFailure(UUID clubId, Source source, UUID competitionId, StatKind kind, String actor,
                               Instant started, RuntimeException e) {
        log.error("stats import failed: source={} kind={}", source.code(), kind.code(), e);
        try {
            repo.insertSyncRun(clubId, source, competitionId, kind, "failed", null, null, List.of(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), actor, started);
        } catch (RuntimeException logFailure) {
            log.error("could not record failed sync run", logFailure);
        }
    }

    /** Line endings and BOM don't change meaning, so they don't change the hash. */
    static String normalise(String content) {
        if (content == null || content.isBlank()) {
            throw new ImportRejectedException(List.of("The content is empty."));
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new ImportRejectedException(List.of("The content is larger than 1 MB."));
        }
        return content.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }

    static String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
