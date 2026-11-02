package money.epsilon.brokerage.platform.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One data row of an {@link ExcelFile.Table}. {@code rowNumber} is the Excel row number (1-based), for messages. */
public final class ExcelRow {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            pattern("d-M-uuuu"), pattern("d/M/uuuu"), pattern("d.M.uuuu"),
            pattern("d-MMM-uuuu"), pattern("d MMM uuuu"), pattern("d-MMM-uu"), pattern("d/MMM/uuuu"));

    private final int rowNumber;
    private final ExcelFile.Columns columns;
    private final Map<Integer, Object> values;

    ExcelRow(int rowNumber, ExcelFile.Columns columns, Map<Integer, Object> values) {
        this.rowNumber = rowNumber;
        this.columns = columns;
        this.values = values;
    }

    public int rowNumber() {
        return rowNumber;
    }

    /** One filled cell with its header as written. */
    public record Cell(String header, String text) {
    }

    /**
     * Every filled cell of the row, in column order, as text (numbers without trailing zeros, dates as yyyy-MM-dd). A
     * header that appears more than once is numbered: "Address Line", "Address Line 2", ….
     */
    public List<Cell> cells() {
        List<Cell> result = new java.util.ArrayList<>();
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> col : columns.labels.entrySet()) {
            int n = seen.merge(col.getValue().toLowerCase(Locale.ENGLISH), 1, Integer::sum);
            Object v = values.get(col.getKey());
            if (v == null) continue;
            String text = v instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : v.toString().trim();
            if (text.isEmpty()) continue;
            result.add(new Cell(n == 1 ? col.getValue() : col.getValue() + " " + n, text));
        }
        return result;
    }

    /** The cell as read: BigDecimal, LocalDate, String, or null (blank / no such column). */
    public Object value(String header) {
        Integer c = columns.indexOf(header);
        return c == null ? null : values.get(c);
    }

    /** The cell in column {@code column} (0-based) as read, for a header that repeats; null when blank. */
    public Object valueAt(int column) {
        return values.get(column);
    }

    /** As text: numbers without trailing zeros, dates as yyyy-MM-dd; blank = "". */
    public String text(String header) {
        Object v = value(header);
        if (v == null) return "";
        if (v instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        return v.toString().trim();
    }

    /** A number cell, or numeric text (commas allowed); blank = null. @throws IllegalArgumentException naming row and column */
    public BigDecimal amount(String header) {
        Object v = value(header);
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNumber + ": '" + s + "' in " + header + " is not an amount");
        }
    }

    /** A date cell, or text in a common date format; blank = null. @throws IllegalArgumentException naming row and column */
    public LocalDate date(String header) {
        Object v = value(header);
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        return parseDate(v.toString(), "Row " + rowNumber + ": " + header);
    }

    /** Text in one of the accepted date formats (2026-04-01, 01-04-2026, 01/04/2026, 01-Apr-2026 …). */
    public static LocalDate parseDate(String text, String what) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException(what + ": '" + text + "' is not a date (use 2026-04-01 or 01-04-2026)");
    }

    private static DateTimeFormatter pattern(String p) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH);
    }
}
