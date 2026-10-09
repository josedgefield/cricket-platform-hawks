package sg.hawkscc.platform.stats.importing;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV/TSV reader for pasted tables: quoted cells, doubled quotes, comma or tab
 * separators, CRLF or LF. Blank lines are skipped.
 */
final class CsvReader {

    private CsvReader() {
    }

    static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String s = text == null ? "" : text.replace("﻿", "");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',' || ch == '\t') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        row.add(cell.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(c -> !c.isBlank())) {
            rows.add(row);
        }
    }
}
