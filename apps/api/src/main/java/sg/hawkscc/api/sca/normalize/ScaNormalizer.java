package sg.hawkscc.api.sca.normalize;

import static sg.hawkscc.api.sca.normalize.Columns.cell;
import static sg.hawkscc.api.sca.normalize.CricketValues.date;
import static sg.hawkscc.api.sca.normalize.CricketValues.decimal;
import static sg.hawkscc.api.sca.normalize.CricketValues.integer;
import static sg.hawkscc.api.sca.normalize.CricketValues.oversToBalls;
import static sg.hawkscc.api.sca.normalize.CricketValues.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.model.RawTable;
import sg.hawkscc.api.sca.model.ScaRecords.BattingStat;
import sg.hawkscc.api.sca.model.ScaRecords.BowlingStat;
import sg.hawkscc.api.sca.model.ScaRecords.FieldingStat;
import sg.hawkscc.api.sca.model.ScaRecords.Fixture;
import sg.hawkscc.api.sca.model.ScaRecords.MatchResult;
import sg.hawkscc.api.sca.model.ScaRecords.Player;

/**
 * Turns raw SCA tables into typed records. Header names are matched through alias lists (see {@link Columns});
 * any column we do not recognise is preserved in each record's {@code extra} map.
 */
@Component
public class ScaNormalizer {

    private static final String[] PLAYER = {"player", "player name", "name", "batsman", "batter", "bowler", "fielder"};
    private static final Pattern VERSUS = Pattern.compile("\\s+(?:v|vs|versus)\\.?\\s+", Pattern.CASE_INSENSITIVE);

    private final ScaProperties props;

    public ScaNormalizer(ScaProperties props) {
        this.props = props;
    }

    public List<Player> players(RawTable t) {
        List<Player> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int name = c.claimContaining(PLAYER);
        int role = c.claimContaining("playing role", "player role", "role");
        int bat = c.claimContaining("batting style", "bat style", "batting");
        int bowl = c.claimContaining("bowling style", "bowl style", "bowling");
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String n = text(cell(r, name));
            if (n == null) {
                continue;
            }
            out.add(new Player(t.idsFor(i).get("playerid"), n, text(cell(r, role)), text(cell(r, bat)),
                    text(cell(r, bowl)), c.extra(r)));
        }
        return out;
    }

    public List<BattingStat> batting(RawTable t) {
        List<BattingStat> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int name = c.claim(PLAYER);
        int mat = c.claim("mat", "matches", "m");
        int inns = c.claim("inns", "innings", "inn", "i");
        int no = c.claim("no", "not outs", "notouts", "n/o", "not out");
        int runs = c.claim("runs", "r", "total runs");
        int balls = c.claim("balls", "b", "bf", "balls faced");
        int hs = c.claim("hs", "highest", "highest score", "best");
        int avg = c.claim("avg", "average", "ave", "bat avg");
        int sr = c.claim("sr", "strike rate", "s/r", "bat sr");
        int fifties = c.claim("50s", "50", "fifties");
        int hundreds = c.claim("100s", "100", "hundreds");
        int fours = c.claim("4s", "fours", "4");
        int sixes = c.claim("6s", "sixes", "6");
        int ducks = c.claim("ducks", "0s", "0");
        if (name < 0) {
            name = c.claimContaining(PLAYER);
        }
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String n = text(cell(r, name));
            if (n == null || isTotalsRow(n)) {
                continue;
            }
            out.add(new BattingStat(t.idsFor(i).get("playerid"), n, integer(cell(r, mat)), integer(cell(r, inns)),
                    integer(cell(r, no)), integer(cell(r, runs)), integer(cell(r, balls)), text(cell(r, hs)),
                    decimal(cell(r, avg)), decimal(cell(r, sr)), integer(cell(r, fifties)),
                    integer(cell(r, hundreds)), integer(cell(r, fours)), integer(cell(r, sixes)),
                    integer(cell(r, ducks)), c.extra(r)));
        }
        return out;
    }

    public List<BowlingStat> bowling(RawTable t) {
        List<BowlingStat> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int name = c.claim(PLAYER);
        int mat = c.claim("mat", "matches");
        int inns = c.claim("inns", "innings", "inn", "i");
        int overs = c.claim("overs", "o", "ov");
        int maidens = c.claim("maidens", "mdns", "mdn", "m");
        int runs = c.claim("runs", "r", "runs conceded");
        int wkts = c.claim("wkts", "wickets", "w", "wkt");
        int best = c.claim("bbi", "best", "bb", "best bowling", "bbm");
        int avg = c.claim("avg", "average", "ave", "bowl avg");
        int econ = c.claim("econ", "economy", "eco", "er", "econ rate");
        int sr = c.claim("sr", "strike rate", "s/r", "bowl sr");
        int wides = c.claim("wd", "wides", "wide", "w/d");
        int noBalls = c.claim("nb", "no balls", "noballs", "n/b");
        int four = c.claim("4w", "4 wkts", "4wi", "4wkts");
        int five = c.claim("5w", "5 wkts", "5wi", "5wkts");
        if (name < 0) {
            name = c.claimContaining(PLAYER);
        }
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String n = text(cell(r, name));
            if (n == null || isTotalsRow(n)) {
                continue;
            }
            String oversText = text(cell(r, overs));
            out.add(new BowlingStat(t.idsFor(i).get("playerid"), n, integer(cell(r, mat)), integer(cell(r, inns)),
                    oversText, oversToBalls(oversText), integer(cell(r, maidens)), integer(cell(r, runs)),
                    integer(cell(r, wkts)), text(cell(r, best)), decimal(cell(r, avg)), decimal(cell(r, econ)),
                    decimal(cell(r, sr)), integer(cell(r, wides)), integer(cell(r, noBalls)),
                    integer(cell(r, four)), integer(cell(r, five)), c.extra(r)));
        }
        return out;
    }

    public List<FieldingStat> fielding(RawTable t) {
        List<FieldingStat> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int name = c.claim(PLAYER);
        int mat = c.claim("mat", "matches", "m");
        int wkCatches = c.claim("wk ct", "wk catches", "wicket keeper catches", "wicketkeeper catches",
                "keeper catches", "ct wk", "wkc", "ct (wk)");
        int catches = c.claim("ct", "catches", "c", "catch", "fielder catches");
        int stumpings = c.claim("st", "stumpings", "stumping", "stmp");
        int runOuts = c.claim("ro", "run outs", "runouts", "run out", "run outs total");
        int total = c.claim("total", "dismissals", "total dismissals", "tot");
        if (name < 0) {
            name = c.claimContaining(PLAYER);
        }
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String n = text(cell(r, name));
            if (n == null || isTotalsRow(n)) {
                continue;
            }
            out.add(new FieldingStat(t.idsFor(i).get("playerid"), n, integer(cell(r, mat)), integer(cell(r, catches)),
                    integer(cell(r, wkCatches)), integer(cell(r, stumpings)), integer(cell(r, runOuts)),
                    integer(cell(r, total)), c.extra(r)));
        }
        return out;
    }

    public List<MatchResult> results(RawTable t) {
        List<MatchResult> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int date = c.claimContaining("date", "match date");
        int comp = c.claimContaining("series", "league", "competition", "tournament", "division");
        int score1 = c.claim("team one score", "team 1 score", "team1 score", "score 1", "home score", "score");
        int score2 = c.claim("team two score", "team 2 score", "team2 score", "score 2", "away score", "score");
        int team1 = c.claim("team one", "team 1", "team1", "home team", "home", "team a", "team", "batting first");
        int team2 = c.claim("team two", "team 2", "team2", "away team", "away", "team b", "team", "opponent",
                "batting second");
        int match = (team1 < 0 && team2 < 0) ? c.claimContaining("match", "teams", "fixture") : -1;
        int result = c.claimContaining("result", "match result", "won by", "winner", "status");
        int venue = c.claimContaining("ground", "venue", "location");
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String[] teams = teams(r, team1, team2, match);
            String dateText = text(cell(r, date));
            String resultText = text(cell(r, result));
            if (dateText == null && teams[0] == null && resultText == null) {
                continue;
            }
            out.add(new MatchResult(t.idsFor(i).get("matchid"), date(dateText, props.dateOrder()), dateText,
                    text(cell(r, comp)), teams[0], text(cell(r, score1)), teams[1], text(cell(r, score2)), resultText,
                    outcome(resultText), text(cell(r, venue)), c.extra(r)));
        }
        return out;
    }

    public List<Fixture> schedule(RawTable t) {
        List<Fixture> out = new ArrayList<>();
        Columns c = new Columns(t.headers());
        int date = c.claimContaining("date", "match date");
        int time = c.claimContaining("time", "start time", "start");
        int comp = c.claimContaining("series", "league", "competition", "tournament", "division");
        int team1 = c.claim("team one", "team 1", "team1", "home team", "home", "team a", "team");
        int team2 = c.claim("team two", "team 2", "team2", "away team", "away", "team b", "team", "opponent");
        int match = (team1 < 0 && team2 < 0) ? c.claimContaining("match", "teams", "fixture") : -1;
        int venue = c.claimContaining("ground", "venue", "location");
        for (int i = 0; i < t.rows().size(); i++) {
            List<String> r = t.rows().get(i);
            String[] teams = teams(r, team1, team2, match);
            String dateText = text(cell(r, date));
            if (dateText == null && teams[0] == null) {
                continue;
            }
            out.add(new Fixture(t.idsFor(i).get("matchid"), date(dateText, props.dateOrder()), dateText,
                    text(cell(r, time)), text(cell(r, comp)), teams[0], teams[1], text(cell(r, venue)), c.extra(r)));
        }
        return out;
    }

    private static String[] teams(List<String> r, int team1, int team2, int match) {
        if (match >= 0) {
            String m = text(cell(r, match));
            if (m != null) {
                String[] parts = VERSUS.split(m, 2);
                return parts.length == 2 ? new String[] {parts[0].trim(), parts[1].trim()} : new String[] {m, null};
            }
            return new String[] {null, null};
        }
        return new String[] {text(cell(r, team1)), text(cell(r, team2))};
    }

    /**
     * Derives WON/LOST/TIED/NO_RESULT/ABANDONED from result text such as "Hawks won by 22 runs". Returns null when
     * the text does not say clearly; the raw text is always kept alongside.
     */
    String outcome(String resultText) {
        if (resultText == null) {
            return null;
        }
        String s = resultText.toLowerCase(Locale.ROOT);
        if (s.contains("abandon")) {
            return "ABANDONED";
        }
        if (s.contains("no result")) {
            return "NO_RESULT";
        }
        if (s.contains("tie")) {
            return "TIED";
        }
        int won = s.indexOf(" won");
        if (won < 0) {
            won = s.indexOf(" beat");
        }
        if (won > 0) {
            String winner = s.substring(0, won);
            return winner.contains(props.teamNameKeyword().toLowerCase(Locale.ROOT)) ? "WON" : "LOST";
        }
        return null;
    }

    private static boolean isTotalsRow(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("total") || n.equals("totals") || n.equals("grand total");
    }
}
