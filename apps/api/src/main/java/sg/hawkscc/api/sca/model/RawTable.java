package sg.hawkscc.api.sca.model;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * A table exactly as the source exported it: header names and cell text, untouched. {@code rowIds} carries ids
 * scraped from links in the row (e.g. {@code playerId}, {@code matchId}); it is empty for a plain CSV download.
 */
public record RawTable(List<String> headers, List<List<String>> rows, List<Map<String, String>> rowIds) {

    public RawTable {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
        rowIds = rowIds == null ? List.of() : rowIds.stream().map(Map::copyOf).toList();
    }

    public static RawTable of(List<String> headers, List<List<String>> rows) {
        return new RawTable(headers, rows, List.of());
    }

    public Map<String, String> idsFor(int rowIndex) {
        return rowIndex < rowIds.size() ? rowIds.get(rowIndex) : Map.of();
    }

    /** Canonical CSV form, used for the raw archive and change detection. */
    public String toCsv() {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180)) {
            printer.printRecord(headers);
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    public String sha256() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(toCsv().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
