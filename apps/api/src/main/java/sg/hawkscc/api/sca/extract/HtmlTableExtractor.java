package sg.hawkscc.api.sca.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sg.hawkscc.api.sca.model.RawTable;

/**
 * Reads the data table that a page's CSV button exports. Picks the table whose header row best matches the
 * dataset's hint words (and has the most rows as a tie-breaker), and keeps cell text exactly as displayed.
 * Ids found in row links (playerId, matchId, ...) are captured alongside.
 */
public final class HtmlTableExtractor {

    private static final Pattern ID_PARAM = Pattern.compile("[?&]((?:player|match|team|club)Id)=(\\d+)", Pattern.CASE_INSENSITIVE);

    private HtmlTableExtractor() {
    }

    public static Optional<RawTable> extract(Document doc, List<String> hints) {
        Element best = null;
        int bestScore = -1;
        int bestRows = -1;
        for (Element table : doc.select("table")) {
            List<String> headers = headers(table);
            if (headers.isEmpty()) {
                continue;
            }
            int score = 0;
            String joined = String.join(" ", headers).toLowerCase(Locale.ROOT);
            for (String hint : hints) {
                if (joined.contains(hint)) {
                    score++;
                }
            }
            int rows = bodyRows(table).size();
            if (score > bestScore || (score == bestScore && rows > bestRows)) {
                best = table;
                bestScore = score;
                bestRows = rows;
            }
        }
        if (best == null || bestScore <= 0) {
            return Optional.empty();
        }
        return Optional.of(toRawTable(best));
    }

    static RawTable toRawTable(Element table) {
        List<String> headers = headers(table);
        List<List<String>> rows = new ArrayList<>();
        List<Map<String, String>> ids = new ArrayList<>();
        for (Element tr : bodyRows(table)) {
            Elements cells = tr.select("> td, > th");
            if (cells.isEmpty() || (cells.size() == 1 && cells.first().hasAttr("colspan"))) {
                continue; // spacer or "No data available" rows
            }
            List<String> row = new ArrayList<>();
            for (Element cell : cells) {
                row.add(clean(cell.text()));
            }
            while (row.size() < headers.size()) {
                row.add("");
            }
            rows.add(row.subList(0, headers.size()));
            ids.add(linkIds(tr));
        }
        return new RawTable(headers, rows, ids);
    }

    private static List<String> headers(Element table) {
        Elements ths = table.select("thead tr").last() != null
                ? table.select("thead tr").last().select("> th, > td")
                : new Elements();
        if (ths.isEmpty()) {
            Element first = table.selectFirst("tr");
            if (first != null && !first.select("> th").isEmpty()) {
                ths = first.select("> th");
            }
        }
        List<String> headers = new ArrayList<>();
        for (Element th : ths) {
            headers.add(clean(th.text()));
        }
        return headers;
    }

    private static List<Element> bodyRows(Element table) {
        Elements body = table.select("tbody > tr");
        if (!body.isEmpty()) {
            return body;
        }
        List<Element> rows = new ArrayList<>(table.select("tr"));
        if (!rows.isEmpty() && !rows.get(0).select("> th").isEmpty()) {
            rows.remove(0);
        }
        return rows;
    }

    private static Map<String, String> linkIds(Element tr) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Element a : tr.select("a[href]")) {
            Matcher m = ID_PARAM.matcher(a.attr("href"));
            while (m.find()) {
                ids.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), m.group(2));
            }
        }
        return ids;
    }

    private static String clean(String text) {
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
