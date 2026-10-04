package sg.hawkscc.platform.stats.importing;

import sg.hawkscc.platform.stats.domain.StatKind;

/**
 * A stored stats column and the leaderboard tab that owns it. When a tab is
 * re-imported, the columns that tab owns are cleared first, so a player who has
 * dropped out of a top-10 list becomes unknown rather than keeping stale figures.
 * {@link #MATCHES} has no owner: several tabs print it and it is never cleared.
 */
public enum StatColumn {
    MATCHES("matches", null, false),
    BAT_INNS("bat_inns", StatKind.BATTING, false),
    BAT_NOT_OUTS("bat_not_outs", StatKind.BATTING, false),
    BAT_RUNS("bat_runs", StatKind.BATTING, false),
    BAT_BALLS("bat_balls", StatKind.BATTING, false),
    BAT_HIGH_SCORE("bat_high_score", StatKind.BATTING, false),
    BAT_FOURS("bat_fours", StatKind.BATTING, false),
    BAT_SIXES("bat_sixes", StatKind.BATTING, false),
    REPORTED_BAT_AVG("reported_bat_avg", StatKind.BATTING, true),
    REPORTED_BAT_SR("reported_bat_sr", StatKind.BATTING, true),
    BOWL_INNS("bowl_inns", StatKind.BOWLING, false),
    BOWL_BALLS("bowl_balls", StatKind.BOWLING, false),
    BOWL_MAIDENS("bowl_maidens", StatKind.BOWLING, false),
    BOWL_RUNS("bowl_runs", StatKind.BOWLING, false),
    BOWL_WICKETS("bowl_wickets", StatKind.BOWLING, false),
    REPORTED_ECON("reported_econ", StatKind.BOWLING, true),
    REPORTED_BOWL_AVG("reported_bowl_avg", StatKind.BOWLING, true),
    FIELD_CATCHES("field_catches", StatKind.FIELDING, false),
    FIELD_STUMPINGS("field_stumpings", StatKind.FIELDING, false),
    FIELD_RUN_OUTS("field_run_outs", StatKind.FIELDING, false),
    FIELD_DISMISSALS("field_dismissals", StatKind.FIELDING, false);

    private final String dbName;
    private final StatKind owner;
    private final boolean decimal;

    StatColumn(String dbName, StatKind owner, boolean decimal) {
        this.dbName = dbName;
        this.owner = owner;
        this.decimal = decimal;
    }

    /** Fixed column name; safe to place in SQL because it never comes from input. */
    public String dbName() {
        return dbName;
    }

    public StatKind owner() {
        return owner;
    }

    public boolean isDecimal() {
        return decimal;
    }
}
