package sg.hawkscc.platform.finance;

/** Whole cents to text for messages, e.g. 123450 → "S$1,234.50". */
final class Money {

    private Money() {
    }

    static String format(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + "S$" + String.format("%,d.%02d", abs / 100, abs % 100);
    }
}
