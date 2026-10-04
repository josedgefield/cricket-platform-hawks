package sg.hawkscc.api.sca.extract;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import sg.hawkscc.api.sca.model.RawTable;

/** Parses a CSV export into a {@link RawTable}. Tolerates a BOM, blank lines, and ragged rows. */
public final class CsvTableReader {

    private CsvTableReader() {
    }

    /** True if the payload looks like CSV rather than an HTML page (e.g. a login or error page). */
    public static boolean looksLikeCsv(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String head = stripBom(body).stripLeading();
        if (head.regionMatches(true, 0, "<!doctype", 0, 9) || head.regionMatches(true, 0, "<html", 0, 5)) {
            return false;
        }
        if (ct.contains("csv") || ct.contains("excel") || ct.contains("octet-stream")) {
            return true;
        }
        String firstLine = head.lines().findFirst().orElse("");
        return firstLine.contains(",") && !firstLine.contains("<");
    }

    public static RawTable read(String csv) throws IOException {
        try (CSVParser parser = CSVParser.parse(new StringReader(stripBom(csv)),
                CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).setTrim(true).get())) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return RawTable.of(List.of(), List.of());
            }
            List<String> headers = new ArrayList<>(records.get(0).toList());
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> row = new ArrayList<>(records.get(i).toList());
                if (row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                while (row.size() < headers.size()) {
                    row.add("");
                }
                rows.add(row.subList(0, headers.size()));
            }
            return RawTable.of(headers, rows);
        }
    }

    private static String stripBom(String s) {
        return s != null && s.startsWith("﻿") ? s.substring(1) : s;
    }
}
