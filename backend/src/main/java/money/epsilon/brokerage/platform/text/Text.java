package money.epsilon.brokerage.platform.text;

import java.util.Locale;

/** Small string helpers for master data typed by people or read from Excel. */
public final class Text {

    private Text() {
    }

    /** Trims and collapses inner whitespace (non-breaking spaces count as spaces); null or blank becomes null. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.replace(' ', ' ').trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /** Matching key: {@link #clean}ed and upper-cased; null/blank becomes "". "Alpha Wealth " → "ALPHA WEALTH". */
    public static String key(String s) {
        String t = clean(s);
        return t == null ? "" : t.toUpperCase(Locale.ROOT);
    }

    /**
     * Name-matching key: {@link #key} with dots ignored, so "A. K. SHAH", "a k  shah" and "A.K. Shah" match. Used for client
     * and partner names across files.
     */
    public static String nameKey(String s) {
        return key(s == null ? null : s.replace('.', ' '));
    }

    /** Compact key: upper case letters and digits only, for headers and codes ("Sub-Broker %" → "SUBBROKER"). */
    public static String compact(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** {@link #clean} that refuses blanks. */
    public static String required(String s, String field) {
        String t = clean(s);
        if (t == null) throw new IllegalArgumentException(field + " is required");
        return t;
    }
}
