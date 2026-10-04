package sg.hawkscc.api.sca.normalize;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sg.hawkscc.api.sca.config.ScaProperties.DateOrder;

/**
 * Parsing helpers for cricket stat cells. Anything that is blank, a dash, or a placeholder such as "DNB" becomes
 * {@code null}: we never turn an unknown into zero.
 */
public final class CricketValues {

    private static final Set<String> UNKNOWN = Set.of("", "-", "--", "—", "–", "na", "n/a", "dnb", "tdnb", "null", "nan");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{1,4})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?[ -]([A-Za-z]{3,9})[ ,-]+(\\d{4})");
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile("([A-Za-z]{3,9})[ -](\\d{1,2})(?:st|nd|rd|th)?[ ,-]+(\\d{4})");
    private static final Map<String, Month> MONTHS = new HashMap<>();

    static {
        for (Month m : Month.values()) {
            MONTHS.put(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT), m);
            MONTHS.put(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT), m);
        }
        MONTHS.put("sept", Month.SEPTEMBER);
    }

    private CricketValues() {
    }

    static boolean isUnknown(String raw) {
        return raw == null || UNKNOWN.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** "45", "45*", "1,204" → number; unknowns → null. */
    public static Integer integer(String raw) {
        if (isUnknown(raw)) {
            return null;
        }
        String s = raw.trim().replace(",", "").replace("*", "");
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal decimal(String raw) {
        if (isUnknown(raw)) {
            return null;
        }
        String s = raw.trim().replace(",", "").replace("*", "");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Overs as written ("23.4") to legal balls (142). Returns null for malformed values such as "3.7". */
    public static Integer oversToBalls(String raw) {
        if (isUnknown(raw)) {
            return null;
        }
        String s = raw.trim();
        try {
            int dot = s.indexOf('.');
            int overs = Integer.parseInt(dot < 0 ? s : s.substring(0, dot));
            int balls = dot < 0 || dot == s.length() - 1 ? 0 : Integer.parseInt(s.substring(dot + 1));
            if (balls < 0 || balls > 5 || overs < 0) {
                return null;
            }
            return overs * 6 + balls;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Text value or null when unknown. */
    public static String text(String raw) {
        return isUnknown(raw) ? null : raw.trim();
    }

    /**
     * Parses dates such as "10/11/2026", "2026-10-11", "11 Oct 2026", "Sat, Oct 11, 2026". Purely numeric
     * day/month dates are read in the configured order. Returns null if the text is not a recognisable date.
     */
    public static LocalDate date(String raw, DateOrder order) {
        if (isUnknown(raw)) {
            return null;
        }
        String s = raw.trim();
        try {
            Matcher m = NUMERIC_DATE.matcher(s);
            if (m.find()) {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                int c = Integer.parseInt(m.group(3));
                if (m.group(1).length() == 4) {
                    return LocalDate.of(a, b, c); // ISO yyyy-MM-dd
                }
                int year = c < 100 ? 2000 + c : c;
                return order == DateOrder.MDY ? LocalDate.of(year, a, b) : LocalDate.of(year, b, a);
            }
            m = DAY_MONTH_YEAR.matcher(s);
            if (m.find() && MONTHS.containsKey(m.group(2).toLowerCase(Locale.ROOT))) {
                return LocalDate.of(Integer.parseInt(m.group(3)), MONTHS.get(m.group(2).toLowerCase(Locale.ROOT)),
                        Integer.parseInt(m.group(1)));
            }
            m = MONTH_DAY_YEAR.matcher(s);
            while (m.find()) {
                Month month = MONTHS.get(m.group(1).toLowerCase(Locale.ROOT));
                if (month != null) {
                    return LocalDate.of(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(2)));
                }
            }
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
        return null;
    }
}
